package dev.sinoka.leashis.core;

import java.util.*;
import java.util.function.Supplier;

/** Server-thread-only state machine. Each consumed lead has exactly one refund owner. */
public final class LeashEngine {
    public record Actor(UUID world, Vec position, Vec velocity, boolean valid,
                        boolean spectator, boolean riding) {
        public boolean eligible() { return valid && !spectator; }
    }
    public interface Port {
        Actor actor(UUID id);
        UUID spawnProxy(UUID target, UUID holder);
        boolean proxyValid(UUID proxy);
        UUID holder(UUID proxy);
        void follow(UUID proxy, UUID target);
        /** Remove the entity while keeping its refund location until removeProxy. */
        void suspendProxy(UUID proxy);
        void removeProxy(UUID proxy);
        void refund(UUID target, UUID proxy);
        void velocity(UUID target, Vec velocity);
    }
    public enum Interaction { PASS, HANDLED, ATTACHED, ALREADY_BOUND }
    public enum PullResult { STARTED, NO_TARGET, COOLDOWN }
    private record Pull(UUID holder, UUID proxy, long until) { }
    public record Session(UUID target, UUID proxy, long attachedAt, boolean refund) { }
    private record Suspended(Session session, UUID holder, boolean respawn) { }
    private record Stamp(UUID target, long tick) { }
    private final Port port;
    private final Supplier<Settings> settings;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Suspended> suspended = new HashMap<>();
    private final Map<UUID, Stamp> interactions = new HashMap<>();
    private final Map<UUID, Pull> pulls = new HashMap<>();
    private final Map<UUID, Long> pullCooldowns = new HashMap<>();
    private final Set<UUID> releaseLocks = new HashSet<>();
    private long tick;

    public LeashEngine(Port port, Supplier<Settings> settings) {
        this.port = port;
        this.settings = settings;
    }
    public boolean isLeashed(UUID target) { return sessions.containsKey(target) || suspended.containsKey(target); }
    public Collection<Session> sessions() { return List.copyOf(sessions.values()); }
    /** Includes anchors held while a target is disconnected. */
    public Set<UUID> holders() {
        Set<UUID> result = new HashSet<>();
        for (Session session : sessions()) {
            UUID holder = port.holder(session.proxy());
            if (holder != null) result.add(holder);
        }
        for (Suspended saved : suspended.values()) if (saved.holder() != null) result.add(saved.holder());
        return Set.copyOf(result);
    }
    public boolean toggleReleaseLock(UUID actor) {
        if (releaseLocks.remove(actor)) return false;
        releaseLocks.add(actor);
        return true;
    }
    public boolean releaseLocked(UUID actor) { return releaseLocks.contains(actor); }
    /** Release active and retained connections without depending on vanilla mob discovery. */
    public void detachFromHolder(UUID holder) {
        for (Session session : sessions())
            if (holder.equals(port.holder(session.proxy()))) detach(session.target());
        for (Suspended saved : List.copyOf(suspended.values()))
            if (holder.equals(saved.holder())) detach(saved.session().target());
    }
    public boolean protectKnot(UUID actor) {
        return isLeashed(actor) && !settings.get().allowKnotRemoval();
    }
    public Interaction interact(UUID actor, UUID target, boolean lead, boolean creative) {
        Actor a = port.actor(actor), t = port.actor(target);
        if (!settings.get().enabled() || actor.equals(target) || !eligible(a) || !eligible(t)
                || !a.world().equals(t.world())) return Interaction.PASS;
        Stamp stamp = interactions.get(actor);
        if (stamp != null && stamp.target().equals(target) && tick - stamp.tick() <= 1)
            return Interaction.HANDLED;
        Session session = sessions.get(target);
        Interaction result;
        if (session != null) {
            if (actor.equals(port.holder(session.proxy()))) {
                if (!releaseLocked(actor) && tick - session.attachedAt() >= 20) detach(target);
                result = Interaction.HANDLED;
            } else if (lead) result = Interaction.ALREADY_BOUND;
            else return Interaction.PASS;
        } else {
            if (!lead || !attach(actor, target, !creative)) return Interaction.PASS;
            result = Interaction.ATTACHED;
        }
        interactions.put(actor, new Stamp(target, tick));
        return result;
    }
    public boolean attach(UUID holder, UUID target, boolean refund) {
        Actor h = port.actor(holder), t = port.actor(target);
        if (!settings.get().enabled() || holder.equals(target) || isLeashed(target)
                || !eligible(h) || !eligible(t) || t.riding() || !h.world().equals(t.world())) return false;
        UUID proxy = port.spawnProxy(target, holder);
        if (proxy == null) return false;
        sessions.put(target, new Session(target, proxy, tick, refund));
        return true;
    }
    /** Reel only the live sessions this player currently holds, along their leash direction. */
    public PullResult pull(UUID player) {
        Actor holder = port.actor(player);
        Settings config = settings.get();
        if (!config.enabled() || !eligible(holder)) return PullResult.NO_TARGET;
        if (tick < pullCooldowns.getOrDefault(player, 0L)) return PullResult.COOLDOWN;
        boolean found = false;
        for (Session session : sessions()) {
            if (!player.equals(port.holder(session.proxy()))) continue;
            Actor target = port.actor(session.target());
            if (!eligible(target) || target.riding() || !port.proxyValid(session.proxy())
                    || !holder.world().equals(target.world())) continue;
            double distance = holder.position().subtract(target.position()).length();
            if (!Double.isFinite(distance) || distance > config.distanceMax()) continue;
            pulls.put(session.target(), new Pull(player, session.proxy(), tick + 60));
            found = true;
        }
        if (!found) return PullResult.NO_TARGET;
        pullCooldowns.put(player, tick + 20);
        return PullResult.STARTED;
    }
    public boolean involves(UUID player) {
        return isLeashed(player) || sessions().stream().anyMatch(s -> player.equals(port.holder(s.proxy())))
                || suspended.values().stream().anyMatch(s -> player.equals(s.holder()));
    }
    public UUID leashHolder(UUID player) {
        Session active = sessions.get(player);
        Suspended saved = suspended.get(player);
        return active != null ? port.holder(active.proxy()) : saved == null ? null : saved.holder();
    }
    public boolean awaitingRespawn(UUID player) {
        Suspended saved = suspended.get(player);
        return saved != null && saved.respawn() && settings.get().keepOnDeath();
    }
    private void suspend(UUID player, boolean respawn) {
        pulls.remove(player);
        Session session = sessions.remove(player);
        if (session == null) return;
        suspended.put(player, new Suspended(session, port.holder(session.proxy()), respawn));
        port.suspendProxy(session.proxy());
    }
    private void detachHeldBy(UUID player) {
        pullCooldowns.remove(player);
        interactions.remove(player);
        for (Session s : sessions())
            if (player.equals(port.holder(s.proxy()))) detach(s.target());
        for (Suspended s : List.copyOf(suspended.values()))
            if (player.equals(s.holder())) detach(s.session().target());
    }
    public void death(UUID player, boolean tiedToFence) {
        if (!awaitingRespawn(player)) {
            if (tiedToFence && settings.get().keepOnDeath()) suspend(player, true);
            else detach(player);
        }
        detachHeldBy(player);
    }
    public void logout(UUID player) {
        releaseLocks.remove(player);
        if (!settings.get().keepOnLogout()) { detachInvolving(player); return; }
        suspend(player, false);
        detachHeldBy(player);
    }
    public void resume(UUID player) { restore(player, false); }
    public void respawn(UUID player) { restore(player, true); }
    private void restore(UUID player, boolean respawn) {
        Suspended saved = suspended.get(player);
        if (saved == null || saved.respawn() != respawn) return;
        Session old = saved.session();
        Actor target = port.actor(player), holder = saved.holder() == null ? null : port.actor(saved.holder());
        if (!settings.get().enabled() || (respawn ? !settings.get().keepOnDeath() : !settings.get().keepOnLogout())
                || !eligible(target) || !eligible(holder) || target.riding() || !target.world().equals(holder.world())) {
            detach(player);
            return;
        }
        UUID proxy = port.spawnProxy(player, saved.holder());
        if (proxy == null) { detach(player); return; }
        suspended.remove(player);
        port.removeProxy(old.proxy());
        sessions.put(player, new Session(player, proxy, old.attachedAt(), old.refund()));
    }
    public void advanceTo(long value) { tick = Math.max(tick, value); }
    public void tick() {
        tick++;
        Settings config = settings.get();
        for (Suspended s : List.copyOf(suspended.values())) {
            Actor holder = s.holder() == null ? null : port.actor(s.holder());
            if (!config.enabled() || (s.respawn() ? !config.keepOnDeath() : !config.keepOnLogout()) || !eligible(holder)) detach(s.session().target());
        }
        for (Session s : sessions()) {
            Actor target = port.actor(s.target());
            UUID holderId = port.holder(s.proxy());
            Actor holder = holderId == null ? null : port.actor(holderId);
            if (!config.enabled() || !eligible(target) || !eligible(holder) || target.riding()
                    || !port.proxyValid(s.proxy()) || !target.world().equals(holder.world())) {
                detach(s.target());
                continue;
            }
            port.follow(s.proxy(), s.target());
            Vec offset = holder.position().subtract(target.position());
            double distance = offset.length();
            if (!Double.isFinite(distance) || distance > config.distanceMax()) detach(s.target());
            else if (!applyPull(s, holderId, target, offset, distance)
                    && distance >= config.distanceMin() && distance > 1.0e-6) {
                Vec next = target.velocity().add(offset.scale(
                        (0.18 + Math.min(0.17, (distance - config.distanceMin()) * 0.04)) / distance));
                double speed = next.length();
                if (!Double.isFinite(speed)) next = Vec.ZERO;
                else if (speed > 1.5) next = next.scale(1.5 / speed);
                port.velocity(s.target(), next);
            }
        }
        interactions.entrySet().removeIf(e -> tick - e.getValue().tick() > 2);
        pullCooldowns.entrySet().removeIf(e -> e.getValue() <= tick);
    }
    private boolean applyPull(Session session, UUID holder, Actor target, Vec offset, double distance) {
        Pull pull = pulls.get(session.target());
        if (pull == null) return false;
        if (pull.until() <= tick || !pull.holder().equals(holder) || !pull.proxy().equals(session.proxy())) {
            pulls.remove(session.target());
            return false;
        }
        if (distance <= 1.05) {
            pulls.remove(session.target());
            if (distance > 1.0e-6) {
                Vec direction = offset.scale(1 / distance);
                Vec velocity = target.velocity();
                double inward = velocity.x()*direction.x() + velocity.y()*direction.y() + velocity.z()*direction.z();
                // Brake only motion toward the holder; preserve sideways movement and falling.
                if (Double.isFinite(inward) && inward > 0)
                    port.velocity(session.target(), velocity.subtract(direction.scale(inward)));
            }
        } else {
            // Ease down near the holder instead of adding momentum that overshoots them.
            double speed = Math.min(0.8, (distance - 1.0) * 0.5);
            port.velocity(session.target(), offset.scale(speed / distance));
        }
        return true;
    }
    public void detach(UUID target) {
        pulls.remove(target);
        Session session = sessions.remove(target);
        if (session == null) {
            Suspended saved = suspended.remove(target);
            if (saved == null) return;
            session = saved.session();
        }
        try { if (session.refund()) port.refund(target, session.proxy()); }
        finally { port.removeProxy(session.proxy()); }
    }
    public void detachInvolving(UUID player) {
        pullCooldowns.remove(player);
        interactions.remove(player);
        for (Suspended s : List.copyOf(suspended.values()))
            if (s.session().target().equals(player) || player.equals(s.holder())) detach(s.session().target());
        for (Session s : sessions()) {
            if (s.target().equals(player) || player.equals(port.holder(s.proxy()))) detach(s.target());
        }
    }
    public void stop() {
        for (UUID target : List.copyOf(suspended.keySet())) detach(target);
        for (Session s : sessions()) detach(s.target());
        interactions.clear(); pulls.clear(); pullCooldowns.clear(); releaseLocks.clear();
    }
    private static boolean eligible(Actor actor) { return actor != null && actor.eligible(); }
}

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
        void removeProxy(UUID proxy);
        void refund(UUID target, UUID proxy);
        void velocity(UUID target, Vec velocity);
    }
    public enum Interaction { PASS, HANDLED, ATTACHED, ALREADY_BOUND }
    public record Session(UUID target, UUID proxy, long attachedAt, boolean refund) { }
    private record Stamp(UUID target, long tick) { }
    private final Port port;
    private final Supplier<Settings> settings;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Stamp> interactions = new HashMap<>();
    private long tick;

    public LeashEngine(Port port, Supplier<Settings> settings) {
        this.port = port;
        this.settings = settings;
    }
    public boolean isLeashed(UUID target) { return sessions.containsKey(target); }
    public Collection<Session> sessions() { return List.copyOf(sessions.values()); }
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
                if (tick - session.attachedAt() >= 20) detach(target);
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
        if (!settings.get().enabled() || holder.equals(target) || sessions.containsKey(target)
                || !eligible(h) || !eligible(t) || t.riding() || !h.world().equals(t.world())) return false;
        UUID proxy = port.spawnProxy(target, holder);
        if (proxy == null) return false;
        sessions.put(target, new Session(target, proxy, tick, refund));
        return true;
    }
    public void advanceTo(long value) { tick = Math.max(tick, value); }
    public void tick() {
        tick++;
        Settings config = settings.get();
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
            else if (distance >= config.distanceMin() && distance > 1.0e-6) {
                Vec next = target.velocity().add(offset.scale(
                        (0.18 + Math.min(0.17, (distance - config.distanceMin()) * 0.04)) / distance));
                double speed = next.length();
                if (!Double.isFinite(speed)) next = Vec.ZERO;
                else if (speed > 1.5) next = next.scale(1.5 / speed);
                port.velocity(s.target(), next);
            }
        }
        interactions.entrySet().removeIf(e -> tick - e.getValue().tick() > 2);
    }
    public void detach(UUID target) {
        Session session = sessions.remove(target);
        if (session == null) return;
        try { if (session.refund()) port.refund(target, session.proxy()); }
        finally { port.removeProxy(session.proxy()); }
    }
    public void detachInvolving(UUID player) {
        interactions.remove(player);
        for (Session s : sessions()) {
            if (s.target().equals(player) || player.equals(port.holder(s.proxy()))) detach(s.target());
        }
    }
    public void stop() {
        for (Session s : sessions()) detach(s.target());
        interactions.clear();
    }
    private static boolean eligible(Actor actor) { return actor != null && actor.eligible(); }
}

package dev.sinoka.leashis.leash;

import dev.sinoka.leashis.Config;
import dev.sinoka.leashis.Leashis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** All state is owned by the logical server and lives only for the current session. */
public final class LeashManager {
    private static final long RELEASE_DELAY = 20;
    private final Map<UUID, LeashSession> sessions = new HashMap<>();
    private final Map<UUID, InteractionStamp> lastInteractions = new HashMap<>();

    private record InteractionStamp(UUID targetId, long tick) {
    }

    public LeashSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public boolean isLeashed(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public boolean shouldProtectKnot(ServerPlayer player, Entity entity) {
        return entity instanceof LeashFenceKnotEntity && isLeashed(player.getUUID())
                && !Config.ALLOW_LEASHED_REMOVE_FENCE_KNOT.get();
    }

    public boolean tryInteract(ServerPlayer actor, ServerPlayer target, InteractionHand hand) {
        if (!Config.ENABLED.get() || !eligible(actor) || !eligible(target)
                || actor == target || actor.level() != target.level()) {
            return false;
        }

        InteractionStamp last = lastInteractions.get(actor.getUUID());
        long now = actor.serverLevel().getGameTime();
        if (last != null && last.targetId().equals(target.getUUID()) && now - last.tick() <= 1) {
            return true;
        }

        LeashSession existing = sessions.get(target.getUUID());
        if (existing != null) {
            // Consume repeated packets during the grace period without detaching or charging.
            if (existing.holder() == actor) {
                if (target.serverLevel().getGameTime() - existing.attachedAtTick() >= RELEASE_DELAY) {
                    detach(target.getUUID(), DetachReason.MANUAL);
                }
                return handled(actor, target);
            }
            if (actor.getItemInHand(hand).is(Items.LEAD)) {
                message(actor, "leashis.message.already_bound", "이미 다른 곳에 연결된 플레이어입니다.");
                return handled(actor, target);
            }
            return false;
        }

        ItemStack stack = actor.getItemInHand(hand);
        if (!stack.is(Items.LEAD) || !attach(actor, target)) {
            return false;
        }
        if (!actor.isCreative()) {
            stack.shrink(1);
        }
        return handled(actor, target);
    }

    public boolean attach(ServerPlayer holder, ServerPlayer target) {
        if (!Config.ENABLED.get() || holder == target || holder.level() != target.level()
                || !eligible(holder) || !eligible(target) || target.isPassenger()
                || sessions.containsKey(target.getUUID())) {
            return false;
        }
        LeashProxyEntity proxy = new LeashProxyEntity(target);
        if (!target.serverLevel().addFreshEntity(proxy)) {
            proxy.removeProxy();
            return false;
        }
        proxy.joinCollisionTeam();
        proxy.setLeashedTo(holder, true);
        sessions.put(target.getUUID(), new LeashSession(target, proxy,
                target.serverLevel().getGameTime(), !holder.isCreative()));
        return true;
    }

    public void tick(MinecraftServer server) {
        for (LeashSession session : new ArrayList<>(sessions.values())) {
            ServerPlayer target = session.target();
            Entity holder = session.holder();
            DetachReason invalid = invalidReason(session, holder);
            if (invalid != null) {
                detach(target.getUUID(), invalid);
                continue;
            }

            session.proxy().followTarget();
            Vec3 offset = holder.position().subtract(target.position());
            double distance = offset.length();
            int maximum = Config.DISTANCE_MAX.get();
            int minimum = Math.min(Config.DISTANCE_MIN.get(), maximum - 1);
            if (distance > maximum) {
                detach(target.getUUID(), DetachReason.TOO_FAR);
            } else if (distance >= minimum && distance > 1.0e-6) {
                Vec3 impulse = offset.scale((0.18 + Math.min(0.17, (distance - minimum) * 0.04)) / distance);
                Vec3 velocity = target.getDeltaMovement().add(impulse);
                // Bound acceleration under latency or repeated ticks instead of accumulating forever.
                if (velocity.lengthSqr() > 2.25) {
                    velocity = velocity.normalize().scale(1.5);
                }
                target.setDeltaMovement(velocity);
                target.connection.send(new ClientboundSetEntityMotionPacket(target));
            }
        }
    }

    private DetachReason invalidReason(LeashSession session, Entity holder) {
        if (!Config.ENABLED.get()) return DetachReason.DISABLED;
        ServerPlayer target = session.target();
        if (!target.isAlive()) return DetachReason.DEATH;
        if (target.hasDisconnected() || target.isRemoved()) return DetachReason.LOGOUT;
        if (target.isPassenger()) return DetachReason.RIDING;
        if (target.isSpectator() || session.proxy().isRemoved() || holder == null) return DetachReason.PROXY_REMOVED;
        if (!holder.isAlive() || holder.isRemoved()) return DetachReason.DEATH;
        if (holder instanceof ServerPlayer player && (player.hasDisconnected() || player.isSpectator())) {
            return DetachReason.LOGOUT;
        }
        if (target.level() != session.proxy().level() || target.level() != holder.level()) {
            return DetachReason.DIMENSION_CHANGE;
        }
        return null;
    }

    public void detach(UUID playerId, DetachReason reason) {
        // Remove first: competing death/logout/fence events cannot refund the same lead twice.
        LeashSession session = sessions.remove(playerId);
        if (session == null) return;
        if (session.returnLead()) {
            session.proxy().spawnAtLocation(Items.LEAD);
        }
        session.proxy().removeProxy();
        Leashis.LOGGER.debug("Detached {}: {}", playerId, reason);
    }

    private boolean handled(ServerPlayer actor, ServerPlayer target) {
        lastInteractions.put(actor.getUUID(), new InteractionStamp(target.getUUID(), actor.serverLevel().getGameTime()));
        return true;
    }

    public void detachInvolving(UUID playerId, DetachReason reason) {
        lastInteractions.remove(playerId);
        for (LeashSession session : new ArrayList<>(sessions.values())) {
            Entity holder = session.holder();
            if (session.leashedPlayerId().equals(playerId)
                    || holder != null && holder.getUUID().equals(playerId)) {
                detach(session.leashedPlayerId(), reason);
            }
        }
    }

    public void stop() {
        for (UUID playerId : new ArrayList<>(sessions.keySet())) {
            detach(playerId, DetachReason.SERVER_STOP);
        }
        lastInteractions.clear();
    }

    public void clearStaleTeam(MinecraftServer server) {
        var scoreboard = server.getScoreboard();
        var team = scoreboard.getPlayerTeam(LeashProxyEntity.TEAM_NAME);
        if (team != null) {
            for (String member : new ArrayList<>(team.getPlayers())) {
                scoreboard.removePlayerFromTeam(member, team);
            }
        }
    }

    private static boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.hasDisconnected() && !player.isSpectator();
    }

    private static void message(ServerPlayer player, String key, String fallback, Object... args) {
        player.sendSystemMessage(Component.translatableWithFallback(key, fallback, args));
    }
}

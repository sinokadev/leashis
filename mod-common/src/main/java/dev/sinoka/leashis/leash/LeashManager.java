package dev.sinoka.leashis.leash;

import dev.sinoka.leashis.Config;
import dev.sinoka.leashis.core.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

/** Minecraft adapter shared by both mod loaders. No loader API in this class. */
public final class LeashManager implements LeashEngine.Port {
    private final Map<UUID, Entity> entities = new HashMap<>();
    private final Map<UUID, LeashProxyEntity> proxies = new HashMap<>();
    private final LeashEngine engine = new LeashEngine(this, () -> new Settings(Config.ENABLED.get(),
            Config.DISTANCE_MIN.get(), Config.DISTANCE_MAX.get(), Config.ALLOW_LEASHED_REMOVE_FENCE_KNOT.get(), Config.KEEP_ON_LOGOUT.get(), Config.KEEP_ON_DEATH.get()));

    public LeashSession getSession(UUID id) {
        return engine.sessions().stream().filter(s -> s.target().equals(id))
                .map(s -> new LeashSession((ServerPlayer)entities.get(id), proxies.get(s.proxy()), s.attachedAt(), s.refund()))
                .findFirst().orElse(null);
    }
    public boolean shouldBlockInteractions(ServerPlayer player) {
        return Config.BLOCK_INTERACTIONS_WHILE_LEASHED.get() && engine.isLeashed(player.getUUID());
    }
    public boolean isLeashed(UUID id) { return engine.isLeashed(id); }
    public boolean toggleReleaseLock(ServerPlayer player) { return engine.toggleReleaseLock(player.getUUID()); }
    /** Return true only when vanilla's knot interaction must be suppressed. */
    public boolean interactKnot(ServerPlayer player, Entity clicked) {
        if (!(clicked instanceof LeashFenceKnotEntity)) return false;
        if (engine.protectKnot(player.getUUID()) || engine.releaseLocked(player.getUUID())) return true;
        // Vanilla may miss transient proxies (or retained targets with no live proxy).
        // Remove our sessions explicitly, then let vanilla process its ordinary mobs/knot.
        engine.detachFromHolder(clicked.getUUID());
        return false;
    }
    public boolean shouldProtectKnot(ServerPlayer p, Entity e) {
        return e instanceof LeashFenceKnotEntity && engine.protectKnot(p.getUUID());
    }
    public boolean shouldProtectProjectile(Entity entity, net.minecraft.world.damagesource.DamageSource source) {
        return Config.PROTECT_PROJECTILE.get() && entity instanceof LeashFenceKnotEntity
                && (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile
                    || source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE))
                && engine.holders().contains(entity.getUUID());
    }
    public boolean shouldProtectFence(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        if (!Config.PROTECT_FENCE_BREAK.get()) return false;
        var session = getSession(player.getUUID());
        return session != null && session.holder() instanceof LeashFenceKnotEntity knot
                && knot.level() == player.level() && knot.getPos().equals(pos);
    }
    public boolean shouldProtectFence(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (!Config.PROTECT_PISTON.get()) return false;
        for (UUID holder : engine.holders()) {
            Entity anchor = entities.get(holder);
            if (anchor instanceof LeashFenceKnotEntity knot && knot.level() == level && knot.getPos().equals(pos))
                return true;
        }
        return false;
    }
    public boolean tryInteract(ServerPlayer actor, ServerPlayer target, InteractionHand hand) {
        remember(actor); remember(target);
        engine.advanceTo(actor.level().getGameTime());
        var stack = actor.getItemInHand(hand);
        var result = engine.interact(actor.getUUID(), target.getUUID(), stack.is(Items.LEAD), actor.isCreative());
        if (result == LeashEngine.Interaction.ATTACHED && !actor.isCreative()) stack.shrink(1);
        if (result == LeashEngine.Interaction.ALREADY_BOUND)
            actor.sendSystemMessage(Component.literal("이미 다른 곳에 연결된 플레이어입니다."));
        return result != LeashEngine.Interaction.PASS;
    }
    public LeashEngine.PullResult pull(ServerPlayer player) {
        remember(player); engine.advanceTo(player.level().getGameTime());
        return engine.pull(player.getUUID());
    }
    public boolean attach(ServerPlayer holder, ServerPlayer target) {
        remember(holder); remember(target); engine.advanceTo(holder.level().getGameTime());
        return engine.attach(holder.getUUID(), target.getUUID(), !holder.isCreative());
    }
    public void tick(MinecraftServer server) {
        // Respawn events can fire before the connection switches to the replacement player.
        var ready = List.copyOf(pendingRespawns.values());
        pendingRespawns.clear();
        for (ServerPlayer player : ready) if (!player.hasDisconnected() && player.isAlive()) completeRespawn(player);
        engine.advanceTo(server.overworld().getGameTime()); engine.tick();
        for (var session : engine.sessions()) {
            if (entities.get(session.target()) instanceof ServerPlayer player && shouldBlockInteractions(player)) {
                player.stopUsingItem();
                if (player.containerMenu != player.inventoryMenu) player.closeContainer();
            }
        }
        entities.keySet().removeIf(id -> !engine.involves(id));
    }
    public void logout(ServerPlayer player) { engine.logout(player.getUUID()); }
    public void login(ServerPlayer player) {
        remember(player);
        if (engine.awaitingRespawn(player.getUUID()) && player.isAlive()) respawn(player);
        else engine.resume(player.getUUID());
    }
    private final Map<UUID, ServerPlayer> pendingRespawns = new HashMap<>();
    private final Set<UUID> restoringRespawns = new HashSet<>();
    public boolean isRespawning(UUID player) { return restoringRespawns.contains(player); }
    public void death(ServerPlayer player) {
        remember(player);
        UUID holder = engine.leashHolder(player.getUUID());
        engine.death(player.getUUID(), entities.get(holder) instanceof LeashFenceKnotEntity);
    }
    public void respawn(ServerPlayer player) {
        remember(player);
        if (engine.awaitingRespawn(player.getUUID())) pendingRespawns.put(player.getUUID(), player);
    }
    private void completeRespawn(ServerPlayer player) {
        UUID id = player.getUUID();
        remember(player);
        if (!engine.awaitingRespawn(id)) return;
        Entity anchor = entities.get(engine.leashHolder(id));
        if (!(anchor instanceof LeashFenceKnotEntity knot) || knot.isRemoved() || !knot.survives()) {
            engine.detach(id);
            return;
        }
        var level = (net.minecraft.server.level.ServerLevel)knot.level();
        Vec3 position = respawnPosition(level, knot.getPos());
        restoringRespawns.add(id);
        try {
            if (player.teleportTo(level, position.x, position.y, position.z, Set.of(), player.getYRot(), player.getXRot()))
                engine.respawn(id);
            else engine.detach(id);
        } finally { restoringRespawns.remove(id); }
    }
    private static Vec3 respawnPosition(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos fence) {
        for (int dy : new int[] {0, 1, -1, 2, -2}) {
            for (int radius = 1; radius <= 2; radius++) {
                for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    var safe = net.minecraft.world.entity.vehicle.DismountHelper.findSafeDismountLocation(
                            net.minecraft.world.entity.EntityType.PLAYER, level, fence.offset(dx, dy, dz), true);
                    if (safe != null) return safe;
                }
            }
        }
        // A fence can support a player when its surroundings offer no standing space.
        return Vec3.atBottomCenterOf(fence).add(0, 1.5, 0);
    }
    public void detach(UUID id, DetachReason reason) { engine.detach(id); }
    public void detachInvolving(UUID id, DetachReason reason) { engine.detachInvolving(id); }
    public void stop() { engine.stop(); entities.clear(); pendingRespawns.clear(); }
    public void clearStaleTeam(MinecraftServer server) {
        var scoreboard = server.getScoreboard();
        var team = scoreboard.getPlayerTeam(LeashProxyEntity.TEAM_NAME);
        if (team != null) for (String member : new ArrayList<>(team.getPlayers())) scoreboard.removePlayerFromTeam(member, team);
    }
    private void remember(Entity e) { entities.put(e.getUUID(), e); }
    @Override public LeashEngine.Actor actor(UUID id) {
        Entity e = entities.get(id);
        if (e == null) return null;
        return new LeashEngine.Actor(UUID.nameUUIDFromBytes(e.level().dimension().location().toString()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)), vec(e.position()), vec(e.getDeltaMovement()),
                e.isAlive() && !e.isRemoved() && (!(e instanceof ServerPlayer p) || !p.hasDisconnected()),
                e instanceof ServerPlayer p && p.isSpectator(), e.isPassenger());
    }
    @Override public UUID spawnProxy(UUID target, UUID holder) {
        var proxy = new LeashProxyEntity((ServerPlayer)entities.get(target));
        if (!((ServerPlayer)entities.get(target)).serverLevel().addFreshEntity(proxy)) { proxy.removeProxy(); return null; }
        proxy.joinCollisionTeam(); proxy.setLeashedTo(entities.get(holder), true);
        proxies.put(proxy.getUUID(), proxy); return proxy.getUUID();
    }
    @Override public boolean proxyValid(UUID id) { var p = proxies.get(id); return p != null && !p.isRemoved(); }
    @Override public UUID holder(UUID id) {
        var proxy = proxies.get(id); Entity h = proxy == null ? null : proxy.getLeashHolder();
        if (h != null) remember(h);
        return h == null ? null : h.getUUID();
    }
    @Override public void follow(UUID proxy, UUID target) { proxies.get(proxy).followTarget(); }
    @Override public void suspendProxy(UUID id) { var p = proxies.get(id); if (p != null) p.removeProxy(); }
    @Override public void removeProxy(UUID id) { var p = proxies.remove(id); if (p != null) p.removeProxy(); }
    @Override public void refund(UUID target, UUID proxy) { proxies.get(proxy).spawnAtLocation(Items.LEAD); }
    @Override public void velocity(UUID target, Vec velocity) {
        var p = (ServerPlayer)entities.get(target);
        p.setDeltaMovement(new Vec3(velocity.x(), velocity.y(), velocity.z()));
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
    }
    private static Vec vec(Vec3 v) { return new Vec(v.x, v.y, v.z); }
}

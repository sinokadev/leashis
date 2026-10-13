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
            Config.DISTANCE_MIN.get(), Config.DISTANCE_MAX.get(), Config.ALLOW_LEASHED_REMOVE_FENCE_KNOT.get()));

    public LeashSession getSession(UUID id) {
        return engine.sessions().stream().filter(s -> s.target().equals(id))
                .map(s -> new LeashSession((ServerPlayer)entities.get(id), proxies.get(s.proxy()), s.attachedAt(), s.refund()))
                .findFirst().orElse(null);
    }
    public boolean isLeashed(UUID id) { return engine.isLeashed(id); }
    public boolean shouldProtectKnot(ServerPlayer p, Entity e) {
        return e instanceof LeashFenceKnotEntity && engine.protectKnot(p.getUUID());
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
    public boolean attach(ServerPlayer holder, ServerPlayer target) {
        remember(holder); remember(target); engine.advanceTo(holder.level().getGameTime());
        return engine.attach(holder.getUUID(), target.getUUID(), !holder.isCreative());
    }
    public void tick(MinecraftServer server) {
        engine.advanceTo(server.overworld().getGameTime()); engine.tick();
        Set<UUID> live = new HashSet<>();
        for (var s : engine.sessions()) {
            live.add(s.target()); UUID holder = holder(s.proxy()); if (holder != null) live.add(holder);
        }
        entities.keySet().retainAll(live);
    }
    public void detach(UUID id, DetachReason reason) { engine.detach(id); }
    public void detachInvolving(UUID id, DetachReason reason) { engine.detachInvolving(id); }
    public void stop() { engine.stop(); entities.clear(); }
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
    @Override public void removeProxy(UUID id) { var p = proxies.remove(id); if (p != null) p.removeProxy(); }
    @Override public void refund(UUID target, UUID proxy) { proxies.get(proxy).spawnAtLocation(Items.LEAD); }
    @Override public void velocity(UUID target, Vec velocity) {
        var p = (ServerPlayer)entities.get(target);
        p.setDeltaMovement(new Vec3(velocity.x(), velocity.y(), velocity.z()));
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
    }
    private static Vec vec(Vec3 v) { return new Vec(v.x, v.y, v.z); }
}

package dev.sinoka.leashis.fabric;

import dev.sinoka.leashis.Config;
import dev.sinoka.leashis.leash.*;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

public final class LeashisFabric implements DedicatedServerModInitializer {
    public static LeashManager manager;
    @Override public void onInitializeServer() {
        try { Config.load(FabricLoader.getInstance().getConfigDir().resolve("leashis-server.properties")); }
        catch (Exception e) { throw new IllegalStateException("Cannot load Leashis settings", e); }
        manager = new LeashManager();
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, environment) -> LeashCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> manager.clearStaleTeam(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> manager.stop());
        ServerTickEvents.END_SERVER_TICK.register(server -> manager.tick(server));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                manager.logout(handler.player));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> manager.login(handler.player));
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) manager.death(player);
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register(
                (oldPlayer, newPlayer, alive) -> manager.respawn(newPlayer));
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
                !(player instanceof ServerPlayer actor) || !manager.shouldProtectFence(actor, pos));
        UseEntityCallback.EVENT.register((player, world, hand, clicked, hit) -> {
            if (!(player instanceof ServerPlayer actor)) return InteractionResult.PASS;
            if (manager.shouldProtectKnot(actor, clicked)) return InteractionResult.FAIL;
            ServerPlayer target = clicked instanceof ServerPlayer p ? p
                    : clicked instanceof LeashProxyEntity proxy ? proxy.target() : null;
            return target != null && manager.tryInteract(actor, target, hand)
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, world, hand, clicked, hit) ->
                player instanceof ServerPlayer actor && manager.shouldProtectKnot(actor, clicked)
                        ? InteractionResult.FAIL : InteractionResult.PASS);
    }
}

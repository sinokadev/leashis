package dev.sinoka.leashis.leash;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class LeashEvents {
    private final LeashManager manager;

    public LeashEvents(LeashManager manager) {
        this.manager = manager;
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        handleInteraction(event, event.getTarget());
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        handleInteraction(event, event.getTarget());
    }

    private void handleInteraction(PlayerInteractEvent event, Entity clicked) {
        if (!(event.getEntity() instanceof ServerPlayer actor)) return;
        boolean handled = manager.shouldProtectKnot(actor, clicked);
        ServerPlayer target = clicked instanceof ServerPlayer player ? player
                : clicked instanceof LeashProxyEntity proxy ? proxy.target() : null;
        if (!handled && target != null) {
            handled = manager.tryInteract(actor, target, event.getHand());
        }
        if (handled) {
            if (event instanceof PlayerInteractEvent.EntityInteract general) {
                general.setCancellationResult(InteractionResult.SUCCESS);
                general.setCanceled(true);
            } else if (event instanceof PlayerInteractEvent.EntityInteractSpecific specific) {
                specific.setCancellationResult(InteractionResult.SUCCESS);
                specific.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && manager.shouldProtectKnot(player, event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && manager.shouldProtectFence(player, event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onDimensionTravel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && manager.isRespawning(player.getUUID())) return;
        if (event.getEntity() instanceof LeashProxyEntity
                || event.getEntity() instanceof ServerPlayer player && manager.isLeashed(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        manager.tick(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            manager.death(player);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) manager.respawn(player);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) manager.login(player);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            manager.logout(player);
        }
    }

    @SubscribeEvent
    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !manager.isRespawning(player.getUUID())) {
            manager.detachInvolving(player.getUUID(), DetachReason.DIMENSION_CHANGE);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        LeashCommands.register(event.getDispatcher(), manager);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        manager.clearStaleTeam(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        manager.stop();
    }
}

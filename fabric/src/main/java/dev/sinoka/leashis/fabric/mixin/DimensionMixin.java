package dev.sinoka.leashis.fabric.mixin;

import dev.sinoka.leashis.fabric.LeashisFabric;
import dev.sinoka.leashis.leash.DetachReason;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers commands as well as portals; Fabric has no cancellable dimension-travel event. */
@Mixin(ServerPlayer.class)
abstract class DimensionMixin {
    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
    private void leashis$travel(DimensionTransition transition, CallbackInfoReturnable<Entity> callback) {
        ServerPlayer self = (ServerPlayer)(Object)this;
        if (LeashisFabric.manager == null || self.level() == transition.newLevel()) return;
        if (LeashisFabric.manager.isLeashed(self.getUUID())) callback.setReturnValue(null);
        else LeashisFabric.manager.detachInvolving(self.getUUID(), DetachReason.DIMENSION_CHANGE);
    }
}

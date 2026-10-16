package dev.sinoka.leashis.leash.mixin;

import dev.sinoka.leashis.Leashis;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
abstract class ItemPickupMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void leashis$pickup(Player player, CallbackInfo callback) {
        if (player instanceof ServerPlayer actor && Leashis.manager != null
                && Leashis.manager.shouldBlockInteractions(actor)) callback.cancel();
    }
}

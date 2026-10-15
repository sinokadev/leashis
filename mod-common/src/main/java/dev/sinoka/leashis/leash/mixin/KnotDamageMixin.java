package dev.sinoka.leashis.leash.mixin;

import dev.sinoka.leashis.Leashis;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fence knots receive projectile damage through their nonliving base class. */
@Mixin(BlockAttachedEntity.class)
abstract class KnotDamageMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void leashis$protectProjectile(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        var manager = Leashis.manager;
        if (manager != null && manager.shouldProtectProjectile((Entity)(Object)this, source))
            callback.setReturnValue(false);
    }
}

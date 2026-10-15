package dev.sinoka.leashis.leash.mixin;

import dev.sinoka.leashis.Leashis;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Check the complete structure, including blocks carried by slime and honey. */
@Mixin(PistonStructureResolver.class)
abstract class PistonMixin {
    @Shadow @Final private Level level;
    @Shadow public abstract List<BlockPos> getToPush();
    @Shadow public abstract List<BlockPos> getToDestroy();

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void leashis$protectAnchor(CallbackInfoReturnable<Boolean> callback) {
        var manager = Leashis.manager;
        if (level.isClientSide() || manager == null || !callback.getReturnValue()) return;
        if (getToPush().stream().anyMatch(pos -> manager.shouldProtectFence(level, pos))
                || getToDestroy().stream().anyMatch(pos -> manager.shouldProtectFence(level, pos)))
            callback.setReturnValue(false);
    }
}

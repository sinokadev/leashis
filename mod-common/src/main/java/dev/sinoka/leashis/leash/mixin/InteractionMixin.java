package dev.sinoka.leashis.leash.mixin;

import dev.sinoka.leashis.Leashis;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reject actions on the server thread, after vanilla schedules network packets there. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class InteractionMixin {
    @Shadow public ServerPlayer player;

    @Unique
    private boolean leashis$blocked() {
        return player.level().getServer().isSameThread() && Leashis.manager != null
                && Leashis.manager.shouldBlockInteractions(player);
    }
    @Unique
    private void leashis$inventory() {
        player.stopUsingItem();
        player.containerMenu.sendAllDataToRemote();
        if (player.containerMenu != player.inventoryMenu) player.inventoryMenu.sendAllDataToRemote();
    }
    @Unique
    private void leashis$updateBlock(net.minecraft.core.BlockPos pos) {
        // A rejected packet must not load chunks at arbitrary client-supplied positions.
        if (player.blockPosition().distSqr(pos) <= 1024)
            player.connection.send(new ClientboundBlockUpdatePacket(player.level(), pos));
    }
    @Inject(method = {"handleInteract", "handleContainerClick", "handleContainerButtonClick", "handleSetCreativeModeSlot",
            "handlePlaceRecipe", "handleEditBook", "handleSignUpdate", "handleSetBeaconPacket", "handleRenameItem", "handlePickItem"},
            at = @At("HEAD"), cancellable = true)
    private void leashis$interaction(CallbackInfo callback) {
        if (leashis$blocked()) { leashis$inventory(); callback.cancel(); }
    }
    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void leashis$action(ServerboundPlayerActionPacket packet, CallbackInfo callback) {
        if (!leashis$blocked()) return;
        // Undo client block prediction as well as inventory predictions (drop/swap).
        leashis$updateBlock(packet.getPos());
        player.connection.send(new ClientboundBlockChangedAckPacket(packet.getSequence()));
        leashis$inventory();
        callback.cancel();
    }
    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void leashis$block(ServerboundUseItemOnPacket packet, CallbackInfo callback) {
        if (!leashis$blocked()) return;
        var hit = packet.getHitResult();
        leashis$updateBlock(hit.getBlockPos());
        leashis$updateBlock(hit.getBlockPos().relative(hit.getDirection()));
        player.connection.send(new ClientboundBlockChangedAckPacket(packet.getSequence()));
        leashis$inventory();
        callback.cancel();
    }
    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void leashis$item(ServerboundUseItemPacket packet, CallbackInfo callback) {
        if (!leashis$blocked()) return;
        player.connection.send(new ClientboundBlockChangedAckPacket(packet.getSequence()));
        leashis$inventory();
        callback.cancel();
    }
}

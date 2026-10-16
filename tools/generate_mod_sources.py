#!/usr/bin/env python3
"""Generate small compile-time API adaptations; never use reflection into Minecraft."""
import argparse, re, shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('version');p.add_argument('loader');p.add_argument('output');a=p.parse_args()
root=Path(__file__).resolve().parent.parent
out=Path(a.output);shutil.rmtree(out,ignore_errors=True);out.mkdir(parents=True)
v=tuple(map(int,a.version.split('.')))
def at(version):return v>=tuple(map(int,version.split('.')))
for file in (root/'mod-common/src/main/java').rglob('*.java'):
 s=file.read_text()
 if file.name=='LeashProxyEntity.java':
  # Reject both chunk persistence and explicit/passenger serialization.
  if at('1.21.6'):
   s=s.replace('saveAsPassenger(CompoundTag tag)', 'saveAsPassenger(net.minecraft.world.level.storage.ValueOutput tag)')
  if at('1.21.2'):
   s=re.sub(r'    @Override\n    public boolean hurt\(DamageSource source, float amount\) \{.*?\n    \}\n','',s,flags=re.S)
  if not at('1.21'):
   s=re.sub(r'    @Override\n    public boolean canUsePortal\(boolean allowPassengers\) \{.*?\n    \}\n','',s,flags=re.S)
  if at('1.21.5'):
   s=s.replace('public void dropLeash(boolean broadcastPacket, boolean dropItem)',
       'public void dropLeash(net.minecraft.server.level.ServerLevel world, boolean broadcastPacket, boolean dropItem)')
   s=s.replace('super.dropLeash(broadcastPacket, false)','super.dropLeash(world, broadcastPacket, false)')
   s=s.replace('dropLeash(true, false);','dropLeash((net.minecraft.server.level.ServerLevel)level(), true, false);')
  if at('1.21.4'):
   s=re.sub(r'    @Override\n    public void dropLeash\(.*?\n    \}', '    @Override\n    public void dropLeash() { super.removeLeash(); }', s, flags=re.S)
   s=s.replace('dropLeash((net.minecraft.server.level.ServerLevel)level(), true, false);','removeLeash();').replace('dropLeash(true, false);','removeLeash();')
  if at('26.2'):s=s.replace('EntityType.RABBIT','net.minecraft.world.entity.EntityTypes.RABBIT')
  if at('26.3'):s=s.replace('setInvulnerable(true)','setPermanentlyInvulnerable(true)')
  if at('1.21.11'):s=s.replace('net.minecraft.world.entity.animal.Rabbit','net.minecraft.world.entity.animal.rabbit.Rabbit')
 if file.name=='KnotDamageMixin.java':
  if not at('1.21'):s=s.replace('BlockAttachedEntity', 'HangingEntity')
  if at('1.21.2'):
   s=s.replace('@Inject(method = "hurt",', '@Inject(method = "hurtServer",')
   s=s.replace('leashis$protectProjectile(DamageSource source,', 'leashis$protectProjectile(net.minecraft.server.level.ServerLevel level, DamageSource source,')
 if file.name in ['PistonMixin.java','KnotDamageMixin.java','InteractionMixin.java','ItemPickupMixin.java'] and a.loader=='fabric':
  s=s.replace('import dev.sinoka.leashis.Leashis;', 'import dev.sinoka.leashis.fabric.LeashisFabric;').replace('Leashis.manager', 'LeashisFabric.manager')
 if file.name=='InteractionMixin.java':
  if at('1.21.4'):
   s=s.replace('"handlePickItem"', '"handlePickItemFromBlock", "handlePickItemFromEntity"')
  if at('26.2'):
   s=s.replace('"handleInteract",', '"handleInteract", "handleAttack",')
  if at('26.3'):
   # Only the use packets became records; PlayerActionPacket still has getters.
   head, tail = s.split('    @Inject(method = "handleUseItemOn"', 1)
   s=head+'    @Inject(method = "handleUseItemOn"'+tail.replace('packet.getHitResult()', 'packet.hitResult()').replace('packet.getSequence()', 'packet.sequence()')
 if file.name=='LeashCommands.java' and at('1.21.11'):
  s=s.replace('.requires(source -> source.hasPermission(2))', '.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))')
 if file.name=='LeashManager.java' and at('1.21.2'):
  s=s.replace('Set.of(), player.getYRot(), player.getXRot())', 'Set.of(), player.getYRot(), player.getXRot(), false)')
  s=s.replace('proxies.get(proxy).spawnAtLocation(Items.LEAD)',
      'proxies.get(proxy).spawnAtLocation((net.minecraft.server.level.ServerLevel)proxies.get(proxy).level(), Items.LEAD)')
 if at('26.2'):s=s.replace('net.minecraft.world.entity.EntityType.PLAYER', 'net.minecraft.world.entity.EntityTypes.PLAYER')
 if at('1.21.6'):s=s.replace('.serverLevel()', '.level()')
 if at('1.21.11'):s=s.replace('.location()', '.identifier()')
 dest=out/file.relative_to(root/'mod-common/src/main/java');dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(s)
# Fabric needs one cancellable hook in addition to its public events.
if a.loader=='fabric':
 source=root/'fabric/src/main/java/dev/sinoka/leashis/fabric/mixin/DimensionMixin.java'
 s=source.read_text()
 if not at('1.21'):
  s=s.replace('import net.minecraft.world.level.portal.DimensionTransition;', 'import net.minecraft.server.level.ServerLevel;')
  s=s.replace('DimensionTransition transition','ServerLevel transition').replace('transition.newLevel()','transition')
 if at('1.21.2'):s=s.replace('changeDimension','teleport').replace('DimensionTransition','TeleportTransition')
 dest=out/'dev/sinoka/leashis/fabric/mixin/DimensionMixin.java';dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(s)

if a.loader=='neoforge':
 for file in (root/'neoforge/src/main/java').rglob('*.java'):
  if file.name=='LeashisClient.java':continue
  s=file.read_text()
  if not at('1.20.5') and file.name=='Leashis.java':
   s=s.replace('public Leashis(ModContainer container)', 'public Leashis()').replace('container.registerConfig(', 'net.neoforged.fml.ModLoadingContext.get().registerConfig(')
  if not at('1.20.5') and file.name=='LeashEvents.java':
   s=s.replace('import net.neoforged.neoforge.event.tick.ServerTickEvent;', 'import net.neoforged.neoforge.event.TickEvent;')
   s=s.replace('ServerTickEvent.Post event', 'TickEvent.ServerTickEvent event').replace('manager.tick(event.getServer());', 'if (event.phase == TickEvent.Phase.END) manager.tick(event.getServer());')
  if file.name=='LeashEvents.java' and at('26.1.2'):
   s=s.replace('net.neoforged.neoforge.event.level.BlockEvent.BreakEvent',
       'net.neoforged.neoforge.event.level.block.BreakBlockEvent')
   s=s.replace('&& manager.shouldProtectFence(player, event.getPos())) event.setCanceled(true);',
       '&& manager.shouldProtectFence(player, event.getPos())) { event.setCanceled(true); event.setNotifyClient(true); }')
  if at('26.3'):
   s=s.replace('ModConfig.Type.SERVER','ModConfig.Type.LOCAL')
  if at('26.2'):
   if file.name=='LeashEvents.java':
    s=re.sub(r'    @SubscribeEvent\n    public void onEntityInteractSpecific\(.*?\n    \}\n','',s,flags=re.S)
    s=re.sub(r' \} else if \(event instanceof PlayerInteractEvent.EntityInteractSpecific specific\) \{.*?specific.setCanceled\(true\);', '',s,flags=re.S)
  dest=out/file.relative_to(root/'neoforge/src/main/java');dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(s)

if a.loader=='fabric':
 extra = ', boolean resetCamera' if at('1.21.2') else ''
 descriptor = 'Z' if at('1.21.2') else ''
 command_source = """package dev.sinoka.leashis.fabric.mixin;
import dev.sinoka.leashis.fabric.LeashisFabric;
import dev.sinoka.leashis.leash.DetachReason;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Commands use this path directly on older versions, bypassing changeDimension. */
@Mixin(ServerPlayer.class)
abstract class CommandTeleportMixin {
    private boolean leashis$block(ServerLevel destination) {
        ServerPlayer self = (ServerPlayer)(Object)this;
        if (LeashisFabric.manager == null || LeashisFabric.manager.isRespawning(self.getUUID()) || self.level() == destination) return false;
        if (LeashisFabric.manager.isLeashed(self.getUUID())) return true;
        LeashisFabric.manager.detachInvolving(self.getUUID(), DetachReason.DIMENSION_CHANGE);
        return false;
    }
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF%s)Z", at = @At("HEAD"), cancellable = true)
    private void leashis$command(ServerLevel world, double x, double y, double z, Set<?> relative,
                                float yaw, float pitch%s, CallbackInfoReturnable<Boolean> callback) {
        if (leashis$block(world)) callback.setReturnValue(false);
    }
%s
}
"""
 void_hook = """    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void leashis$direct(ServerLevel world, double x, double y, double z, float yaw, float pitch, CallbackInfo callback) {
        if (leashis$block(world)) callback.cancel();
    }""" if not at('1.21.2') else ''
 dest=out/'dev/sinoka/leashis/fabric/mixin/CommandTeleportMixin.java'
 dest.write_text(command_source % (descriptor,extra,void_hook))

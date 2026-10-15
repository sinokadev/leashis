package dev.sinoka.leashis.gametest;

import com.mojang.authlib.GameProfile;
import dev.sinoka.leashis.Config;
import dev.sinoka.leashis.leash.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("leashis")
@PrefixGameTestTemplate(false)
public final class LeashGameTests {
    private record Fixture(GameTestHelper helper, LeashManager manager, FakePlayer holder, net.minecraft.server.level.ServerPlayer target) {
        boolean connect() {
            return manager.tryInteract(holder, target, InteractionHand.MAIN_HAND);
        }

        void tick() {
            manager.tick(helper.getLevel().getServer());
        }

        long leadDrops() {
            return helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(target.position(), target.position()).inflate(2), item -> item.getItem().is(Items.LEAD))
                    .stream().mapToLong(item -> item.getItem().getCount()).sum();
        }
    }

    private static Fixture fixture(GameTestHelper helper) {
        FakePlayer holder = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "LeashHolder"));
        var target = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "LeashTarget"), net.minecraft.server.level.ClientInformation.createDefault());
        target.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), target,
                net.minecraft.server.network.CommonListenerCookie.createInitial(target.getGameProfile(), false)) {
            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet) {
            }

            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) {
            }
        };
        holder.setGameMode(GameType.SURVIVAL);
        target.setGameMode(GameType.SURVIVAL);
        Vec3 origin = helper.absoluteVec(new Vec3(2, 2, 2));
        holder.setPos(origin.x + 1, origin.y, origin.z);
        target.setPos(origin.x, origin.y, origin.z);
        holder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD, 3));
        return new Fixture(helper, new LeashManager(), holder, target);
    }

    @GameTest(template = "empty")
    public static void survivalAndDuplicateInteraction(GameTestHelper helper) {
        Fixture f = fixture(helper);
        try {
            helper.assertTrue(f.connect(), "First interaction must connect");
            var session = f.manager.getSession(f.target.getUUID());
            helper.assertTrue(session != null && session.holder() == f.holder, "Holder must match");
            helper.assertTrue(session.proxy().isInvisible() && session.proxy().isBaby(), "Proxy must be invisible and small");
            helper.assertTrue(f.connect(), "Duplicate packet should be consumed");
            helper.assertTrue(f.holder.getMainHandItem().getCount() == 2, "Consume exactly one lead");
            f.manager.detach(f.target.getUUID(), DetachReason.MANUAL);
            f.manager.detach(f.target.getUUID(), DetachReason.LOGOUT);
            helper.assertTrue(f.leadDrops() == 1, "Refund exactly one lead across competing events");
            helper.assertTrue(session.proxy().isRemoved(), "Remove proxy");
            helper.assertTrue(helper.getLevel().getScoreboard().getPlayersTeam(session.proxy().getScoreboardName()) == null,
                    "Remove collision team membership");
            helper.succeed();
        } finally {
            f.manager.stop();
        }
    }

    @GameTest(template = "empty")
    public static void creativeDoesNotCreateLeads(GameTestHelper helper) {
        Fixture f = fixture(helper);
        try {
            f.holder.setGameMode(GameType.CREATIVE);
            helper.assertTrue(f.connect(), "Creative connection");
            helper.assertTrue(f.holder.getMainHandItem().getCount() == 3, "Creative lead is not consumed");
            f.manager.detach(f.target.getUUID(), DetachReason.TOO_FAR);
            helper.assertTrue(f.leadDrops() == 0, "Creative session must not mint a lead on detach");
            helper.succeed();
        } finally {
            f.manager.stop();
        }
    }

    @GameTest(template = "empty")
    public static void rejectSelfAndOccupiedTarget(GameTestHelper helper) {
        Fixture f = fixture(helper);
        try {
            helper.assertTrue(!f.manager.tryInteract(f.holder, f.holder, InteractionHand.MAIN_HAND), "Reject self");
            f.connect();
            FakePlayer stranger = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "LeashStranger"));
            stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD, 2));
            helper.assertTrue(f.manager.tryInteract(stranger, f.target, InteractionHand.MAIN_HAND), "Occupied click is handled");
            helper.assertTrue(stranger.getMainHandItem().getCount() == 2, "Do not charge stranger");
            helper.assertTrue(f.manager.getSession(f.target.getUUID()).holder() == f.holder, "Cannot steal holder");
            helper.succeed();
        } finally {
            f.manager.stop();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void manualReleaseSurvivesBothInteractionPackets(GameTestHelper helper) {
        Fixture f = fixture(helper);
        f.connect();
        helper.runAfterDelay(21, () -> {
            try {
                var events = new LeashEvents(f.manager);
                var specific = new PlayerInteractEvent.EntityInteractSpecific(f.holder, InteractionHand.MAIN_HAND, f.target, Vec3.ZERO);
                events.onEntityInteractSpecific(specific);
                var general = new PlayerInteractEvent.EntityInteract(f.holder, InteractionHand.MAIN_HAND, f.target);
                events.onEntityInteract(general);
                helper.assertTrue(specific.isCanceled() && general.isCanceled(), "Both packets must be handled");
                helper.assertTrue(!f.manager.isLeashed(f.target.getUUID()), "Second packet must not reattach");
                helper.assertTrue(f.holder.getMainHandItem().getCount() == 2 && f.leadDrops() == 1, "Release refunds once");
                helper.succeed();
            } finally {
                f.manager.stop();
            }
        });
    }

    @GameTest(template = "empty")
    public static void pullingAndDistanceBreak(GameTestHelper helper) {
        Fixture f = fixture(helper);
        try {
            f.connect();
            f.tick();
            helper.assertTrue(f.target.getDeltaMovement().lengthSqr() == 0, "Close rope is slack");
            f.holder.setPos(f.target.getX() + 6, f.target.getY(), f.target.getZ());
            f.tick();
            helper.assertTrue(f.target.getDeltaMovement().x > 0, "Pull toward holder");
            helper.assertTrue(Double.isFinite(f.target.getDeltaMovement().x), "Finite velocity");
            f.holder.setPos(f.target.getX() + 11, f.target.getY(), f.target.getZ());
            f.tick();
            helper.assertTrue(!f.manager.isLeashed(f.target.getUUID()) && f.leadDrops() == 1, "Too far snaps and refunds");
            helper.succeed();
        } finally {
            f.manager.stop();
        }
    }

    @GameTest(template = "empty")
    public static void fenceTransferAndProtectedKnot(GameTestHelper helper) {
        Fixture f = fixture(helper);
        try {
            f.connect();
            BlockPos fence = helper.absolutePos(new BlockPos(3, 1, 2));
            helper.getLevel().setBlockAndUpdate(fence, Blocks.OAK_FENCE.defaultBlockState());
            LeadItem.bindPlayerMobs(f.holder, helper.getLevel(), fence);
            f.tick();
            var session = f.manager.getSession(f.target.getUUID());
            helper.assertTrue(session.holder() instanceof LeashFenceKnotEntity, "Vanilla fence transfer must update holder");
            var events = new LeashEvents(f.manager);
            var click = new PlayerInteractEvent.EntityInteract(f.target, InteractionHand.MAIN_HAND, session.holder());
            events.onEntityInteract(click);
            var attack = new AttackEntityEvent(f.target, session.holder());
            events.onAttack(attack);
            helper.assertTrue(click.isCanceled() && attack.isCanceled(), "Bound player cannot remove knot");
            session.proxy().dropLeash(true, true);
            f.tick();
            helper.assertTrue(!f.manager.isLeashed(f.target.getUUID()) && f.leadDrops() == 1, "Vanilla unlink refunds once");
            helper.succeed();
        } finally {
            f.manager.stop();
        }
    }

    @GameTest(template = "empty")
    public static void boundTargetCannotBreakOwnFence(GameTestHelper helper) {
        Fixture f = fixture(helper);
        boolean allowKnotRemoval = Config.ALLOW_LEASHED_REMOVE_FENCE_KNOT.get();
        try {
            f.connect();
            BlockPos fence = helper.absolutePos(new BlockPos(3, 1, 2));
            helper.getLevel().setBlockAndUpdate(fence, Blocks.OAK_FENCE.defaultBlockState());
            LeadItem.bindPlayerMobs(f.holder, helper.getLevel(), fence);
            var events = new LeashEvents(f.manager);
            // Fence block protection is mandatory even if knot interaction is allowed.
            Config.ALLOW_LEASHED_REMOVE_FENCE_KNOT.set(true);
            for (GameType mode : new GameType[] {GameType.SURVIVAL, GameType.CREATIVE}) {
                f.target.setGameMode(mode);
                var own = new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(
                        helper.getLevel(), fence, Blocks.OAK_FENCE.defaultBlockState(), f.target);
                events.onBlockBreak(own);
                helper.assertTrue(own.isCanceled(), "Bound target cannot break their anchor in " + mode);
            }
            helper.assertTrue(!f.manager.shouldProtectFence(f.target, fence.east()), "Other fences remain breakable");
            var ownerBreak = new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(
                    helper.getLevel(), fence, Blocks.OAK_FENCE.defaultBlockState(), f.holder);
            events.onBlockBreak(ownerBreak);
            helper.assertTrue(!ownerBreak.isCanceled(), "Other players can break the anchor");
            f.manager.detach(f.target.getUUID(), DetachReason.MANUAL);
            helper.assertTrue(!f.manager.shouldProtectFence(f.target, fence), "Release removes fence protection");
            helper.succeed();
        } finally {
            Config.ALLOW_LEASHED_REMOVE_FENCE_KNOT.set(allowKnotRemoval);
            f.manager.stop();
        }
    }

    @GameTest(template = "empty")
    public static void ownerLogoutReleasesEveryTarget(GameTestHelper helper) {
        Fixture f = fixture(helper);
        try {
            f.connect();
            FakePlayer second = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "LeashSecond"));
            second.setPos(f.target.getX() + 1, f.target.getY(), f.target.getZ() + 1);
            helper.assertTrue(f.manager.tryInteract(f.holder, second, InteractionHand.MAIN_HAND), "Multiple targets");
            var firstProxy = f.manager.getSession(f.target.getUUID()).proxy();
            var secondProxy = f.manager.getSession(second.getUUID()).proxy();
            f.manager.detachInvolving(f.holder.getUUID(), DetachReason.LOGOUT);
            helper.assertTrue(!f.manager.isLeashed(f.target.getUUID()) && !f.manager.isLeashed(second.getUUID()), "Release all targets");
            helper.assertTrue(firstProxy.isRemoved() && secondProxy.isRemoved(), "Remove both proxies");
            helper.assertTrue(f.leadDrops() == 2, "Return both paid leads");
            helper.succeed();
        } finally {
            f.manager.stop();
        }
    }

    @GameTest(template = "empty")
    public static void proxyCannotPersistAndTravelIsBlocked(GameTestHelper helper) {
        Fixture f = fixture(helper);
        try {
            f.connect();
            var proxy = f.manager.getSession(f.target.getUUID()).proxy();
            helper.assertTrue(!proxy.shouldBeSaved() && !proxy.save(new CompoundTag()), "Never persist a ghost turtle");
            var travel = new EntityTravelToDimensionEvent(f.target, Level.NETHER);
            new LeashEvents(f.manager).onDimensionTravel(travel);
            helper.assertTrue(travel.isCanceled(), "Block bound-player dimension travel");
            f.manager.stop();
            f.manager.stop();
            helper.assertTrue(proxy.isRemoved() && f.leadDrops() == 1, "Shutdown is idempotent");
            helper.succeed();
        } finally {
            f.manager.stop();
        }
    }

    @GameTest(template = "empty")
    public static void disabledFeatureDetaches(GameTestHelper helper) {
        Fixture f = fixture(helper);
        boolean enabled = Config.ENABLED.get();
        try {
            f.connect();
            Config.ENABLED.set(false);
            f.tick();
            helper.assertTrue(!f.manager.isLeashed(f.target.getUUID()) && f.leadDrops() == 1, "Disable releases existing session");
            helper.assertTrue(!f.connect(), "Disable rejects new session");
            helper.succeed();
        } finally {
            Config.ENABLED.set(enabled);
            f.manager.stop();
        }
    }

    @GameTest(template = "empty")
    public static void targetDeathReleasesOnce(GameTestHelper helper) {
        Fixture f = fixture(helper);
        try {
            f.connect();
            var proxy = f.manager.getSession(f.target.getUUID()).proxy();
            f.target.setHealth(0);
            proxy.tick();
            f.tick();
            f.manager.detachInvolving(f.target.getUUID(), DetachReason.DEATH);
            helper.assertTrue(!f.manager.isLeashed(f.target.getUUID()) && proxy.isRemoved(), "Death removes session and proxy");
            helper.assertTrue(f.leadDrops() == 1, "Proxy and player death paths refund once");
            helper.succeed();
        } finally {
            f.manager.stop();
        }
    }

    @GameTest(template = "empty")
    public static void mountingReleasesTarget(GameTestHelper helper) {
        Fixture f = fixture(helper);
        var mount = net.minecraft.world.entity.EntityType.PIG.create(helper.getLevel());
        try {
            f.connect();
            mount.setPos(f.target.position());
            helper.assertTrue(f.target.startRiding(mount, true), "Mount test vehicle");
            f.tick();
            helper.assertTrue(!f.manager.isLeashed(f.target.getUUID()) && f.leadDrops() == 1, "Mounting releases and refunds");
            helper.succeed();
        } finally {
            f.target.stopRiding();
            mount.discard();
            f.manager.stop();
        }
    }

    @GameTest(template = "empty")
    public static void zeroDistanceAndInvalidThresholdsStayFinite(GameTestHelper helper) {
        Fixture f = fixture(helper);
        int minimum = Config.DISTANCE_MIN.get();
        int maximum = Config.DISTANCE_MAX.get();
        try {
            Config.DISTANCE_MIN.set(0);
            Config.DISTANCE_MAX.set(1);
            f.holder.setPos(f.target.position());
            f.connect();
            f.tick();
            helper.assertTrue(f.target.getDeltaMovement().equals(Vec3.ZERO), "Coincident players must not divide by zero");
            Config.DISTANCE_MIN.set(100);
            Config.DISTANCE_MAX.set(10);
            f.holder.setPos(f.target.getX() + 6, f.target.getY(), f.target.getZ());
            f.tick();
            helper.assertTrue(Double.isFinite(f.target.getDeltaMovement().x), "Invalid thresholds must remain finite");
            helper.assertTrue(f.manager.isLeashed(f.target.getUUID()), "Invalid minimum must not snap within maximum");
            helper.succeed();
        } finally {
            Config.DISTANCE_MIN.set(minimum);
            Config.DISTANCE_MAX.set(maximum);
            f.manager.stop();
        }
    }

}

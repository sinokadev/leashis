package dev.sinoka.leashis;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Enable leashing players.")
            .define("enabled", true);
    public static final ModConfigSpec.IntValue DISTANCE_MIN = BUILDER
            .comment("Distance before pulling starts. Values >= distanceMax are clamped to distanceMax - 1.")
            .defineInRange("distanceMin", 4, 0, 1024);
    public static final ModConfigSpec.IntValue DISTANCE_MAX = BUILDER
            .comment("Distance before the leash breaks. Must be greater than distanceMin.")
            .defineInRange("distanceMax", 10, 1, 1024);
    public static final ModConfigSpec.BooleanValue ALLOW_LEASHED_REMOVE_FENCE_KNOT = BUILDER
            .comment("Allow leashed players to remove fence knots.")
            .define("allowLeashedRemoveFenceKnot", false);

    public static final ModConfigSpec.BooleanValue KEEP_ON_LOGOUT = BUILDER
            .comment("Keep the target's leash when they disconnect and reconnect.")
            .define("keepOnLogout", true);

    public static final ModConfigSpec.BooleanValue KEEP_ON_DEATH = BUILDER
            .comment("Keep fence leashes on death and respawn near the fence.")
            .define("keepOnDeath", true);

    public static void setKeepOnDeath(boolean value) throws java.io.IOException {
        KEEP_ON_DEATH.set(value);
        KEEP_ON_DEATH.save();
    }

    public static void setKeepOnLogout(boolean value) throws java.io.IOException {
        KEEP_ON_LOGOUT.set(value);
        KEEP_ON_LOGOUT.save();
    }

    public static final ModConfigSpec.BooleanValue PROTECT_PROJECTILE = BUILDER
            .comment("Block projectile damage to occupied fence knots.").define("protectProjectile", true);

    public static void setProtectProjectile(boolean value) throws java.io.IOException {
        PROTECT_PROJECTILE.set(value);
        PROTECT_PROJECTILE.save();
    }

    public static final ModConfigSpec.BooleanValue PROTECT_FENCE_BREAK = BUILDER
            .comment("Prevent tied players from breaking their own fence.").define("protectFenceBreak", true);

    public static void setProtectFenceBreak(boolean value) throws java.io.IOException {
        PROTECT_FENCE_BREAK.set(value);
        PROTECT_FENCE_BREAK.save();
    }

    public static final ModConfigSpec.BooleanValue PROTECT_PISTON = BUILDER
            .comment("Prevent pistons from moving occupied fence anchors.").define("protectPiston", true);

    public static void setProtectPiston(boolean value) throws java.io.IOException {
        PROTECT_PISTON.set(value);
        PROTECT_PISTON.save();
    }

    public static final ModConfigSpec.BooleanValue BLOCK_INTERACTIONS_WHILE_LEASHED = BUILDER
            .comment("Block manual interactions and inventory actions while leashed; allow drops and pickup.")
            .define("blockInteractionsWhileLeashed", false);

    public static void setBlockInteractionsWhileLeashed(boolean value) throws java.io.IOException {
        BLOCK_INTERACTIONS_WHILE_LEASHED.set(value);
        BLOCK_INTERACTIONS_WHILE_LEASHED.save();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}

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

    public static void setKeepOnLogout(boolean value) throws java.io.IOException {
        KEEP_ON_LOGOUT.set(value);
        KEEP_ON_LOGOUT.save();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}

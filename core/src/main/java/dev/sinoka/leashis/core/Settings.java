package dev.sinoka.leashis.core;

public record Settings(boolean enabled, int distanceMin, int distanceMax, boolean allowKnotRemoval) {
    public static final Settings DEFAULT = new Settings(true, 4, 10, false);
    public Settings {
        distanceMax = Math.max(1, Math.min(1024, distanceMax));
        distanceMin = Math.max(0, Math.min(distanceMax - 1, distanceMin));
    }
}

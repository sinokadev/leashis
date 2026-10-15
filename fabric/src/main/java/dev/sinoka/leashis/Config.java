package dev.sinoka.leashis;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Small server-only configuration; deliberately has no client-side dependency. */
public final class Config {
    public static final Value<Boolean> ENABLED = new Value<>(true);
    public static final Value<Integer> DISTANCE_MIN = new Value<>(4);
    public static final Value<Integer> DISTANCE_MAX = new Value<>(10);
    public static final Value<Boolean> ALLOW_LEASHED_REMOVE_FENCE_KNOT = new Value<>(false);
    public static final Value<Boolean> KEEP_ON_LOGOUT = new Value<>(true);
    private static Path configPath;
    public static final class Value<T> {
        private T value;
        public Value(T value) { this.value = value; }
        public T get() { return value; }
        public void set(T value) { this.value = value; }
    }
    public static void load(Path path) throws IOException {
        configPath = path;
        Properties properties = new Properties();
        if (Files.exists(path)) try (Reader reader = Files.newBufferedReader(path)) { properties.load(reader); }
        ENABLED.set(bool(properties, "enabled", true));
        DISTANCE_MIN.set(integer(properties, "distanceMin", 4, 0));
        DISTANCE_MAX.set(integer(properties, "distanceMax", 10, 1));
        ALLOW_LEASHED_REMOVE_FENCE_KNOT.set(bool(properties, "allowLeashedRemoveFenceKnot", false));
        KEEP_ON_LOGOUT.set(bool(properties, "keepOnLogout", true));
        if (!Files.exists(path)) {
            properties.setProperty("enabled", "true"); properties.setProperty("distanceMin", "4");
            properties.setProperty("distanceMax", "10"); properties.setProperty("allowLeashedRemoveFenceKnot", "false");
            properties.setProperty("keepOnLogout", "true");
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) { properties.store(writer, "Leashis server settings"); }
        }
    }
    public static void setKeepOnLogout(boolean value) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(configPath)) try (Reader reader = Files.newBufferedReader(configPath)) { properties.load(reader); }
        properties.setProperty("keepOnLogout", Boolean.toString(value));
        try (Writer writer = Files.newBufferedWriter(configPath)) { properties.store(writer, "Leashis server settings"); }
        KEEP_ON_LOGOUT.set(value);
    }
    private static int integer(Properties p, String key, int fallback, int min) {
        return Math.max(min, Math.min(1024, Integer.parseInt(p.getProperty(key, "" + fallback))));
    }
    private static boolean bool(Properties p, String key, boolean fallback) {
        String value = p.getProperty(key, "" + fallback);
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
            throw new IllegalArgumentException("Invalid boolean: " + key);
        return Boolean.parseBoolean(value);
    }
}

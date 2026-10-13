package dev.sinoka.leashis.core;

public record Vec(double x, double y, double z) {
    public static final Vec ZERO = new Vec(0, 0, 0);
    public Vec add(Vec v) { return new Vec(x + v.x, y + v.y, z + v.z); }
    public Vec subtract(Vec v) { return new Vec(x - v.x, y - v.y, z - v.z); }
    public Vec scale(double n) { return new Vec(x * n, y * n, z * n); }
    public double length() { return Math.sqrt(x * x + y * y + z * z); }
}

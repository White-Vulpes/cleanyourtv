package com.gathin.tvhome.ascii;

import java.util.Random;

/** Port of ascii-animals src/math.ts (MIT, drewdi). Plain Java so the animals compile without Android. */
public final class AMath {
    private AMath() {}

    public static final double TAU = Math.PI * 2;
    private static final Random RNG = new Random();

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    /** Math.random() in the original. */
    public static double random() {
        return RNG.nextDouble();
    }

    public static double rand(double lo, double hi) {
        return lo + RNG.nextDouble() * (hi - lo);
    }

    public static double frac(double x) {
        return x - Math.floor(x);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static double smoothstep(double e0, double e1, double x) {
        double t = clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Shortest signed angle from a to b, in (-PI, PI]. JS % keeps the dividend's sign, as Java's does. */
    public static double angleDiff(double a, double b) {
        double d = (b - a) % TAU;
        if (d > Math.PI) d -= TAU;
        if (d < -Math.PI) d += TAU;
        return d;
    }

    /** JavaScript's ToInt32 (what `x | 0` does): truncate, then wrap modulo 2^32. */
    public static int toInt32(double x) {
        if (Double.isNaN(x) || Double.isInfinite(x)) return 0;
        return (int) (long) x;
    }

    /** JavaScript's ToUint32 (what `x >>> 0` does), as a long in [0, 2^32). */
    public static long toUint32(double x) {
        return toInt32(x) & 0xFFFFFFFFL;
    }

    /** Cheap integer hash to [0, 1). Deterministic, so textures stay stable. */
    public static double hash3(double x, double y, double z) {
        int h = (toInt32(x) * 374761393) ^ (toInt32(y) * 668265263) ^ (toInt32(z) * 1442695041);
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFFFFL) / 4294967296.0;
    }
}

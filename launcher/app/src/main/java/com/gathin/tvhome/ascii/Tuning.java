package com.gathin.tvhome.ascii;

import java.util.HashMap;

/**
 * Port of SHARED_TUNING plus each animal's own knobs. The shared knobs are fields; an animal's own knobs live
 * in a map, read with {@link #get(String)} / {@link #is(String)}. Read them once per step or per draw into a
 * local, not inside a sampler, which runs thousands of times a frame.
 */
public final class Tuning {
    // Movement
    public double density = 1;
    public boolean corner = false;
    public double direction = 49;
    public double speed = 1;
    public double sway = 0.04;
    public double spread = 0.06;
    public double size = 1;
    public double farthest = 0.45;
    public double farBias = 1.6;
    public double grip = 2;
    public double separation = 300;

    // Look
    public double hueShift = 0;
    public double shimmer = 0.3;
    public double sparkle = 0.12;
    public double fog = 0.75;
    public double trail = 1;
    public double trailFade = 1.3;
    public double grid = 1.05;
    public double detail = 1.3;

    private final HashMap<String, Double> extra = new HashMap<>();

    /** An animal's own numeric knob (booleans are stored as 1 / 0). 0 when never set. */
    public double get(String key) {
        Double v = extra.get(key);
        return v == null ? 0 : v;
    }

    /** An animal's own switch. */
    public boolean is(String key) {
        return get(key) != 0;
    }

    public Tuning put(String key, double value) {
        extra.put(key, value);
        return this;
    }

    public Tuning put(String key, boolean value) {
        extra.put(key, value ? 1.0 : 0.0);
        return this;
    }
}

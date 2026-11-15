package com.gathin.tvhome.ascii;

/** Port of ascii-animals src/animals/shapes.ts: small geometry helpers for building silhouettes. */
public final class Shapes {
    private Shapes() {}

    /** seg.t in the original: how far along the segment (0 to 1) the nearest point of the last toSegment() is. */
    public static double segT;

    /** Distance from (px, py) to the segment a to b. Sets {@link #segT}. Main thread only. */
    public static double toSegment(double px, double py, double ax, double ay, double bx, double by) {
        double sx = bx - ax;
        double sy = by - ay;
        double len = sx * sx + sy * sy;
        if (len == 0) len = 1;
        double t = AMath.clamp(((px - ax) * sx + (py - ay) * sy) / len, 0, 1);
        segT = t;
        return Math.hypot(px - ax - sx * t, py - ay - sy * t);
    }

    /** Inside the ellipse centred on (cu, cv) with half-axes a (along u) and b (along v)? */
    public static boolean inEllipse(double u, double v, double cu, double cv, double a, double b) {
        double x = (u - cu) / a;
        double y = (v - cv) / b;
        return x * x + y * y <= 1;
    }

    /** Round spots on a jittered grid of cell-sized squares, seeded so each animal gets its own. */
    public static boolean spotted(double u, double v, double cell, double radius, double seed) {
        double gu = u / cell;
        double gv = v / cell;
        double cu = Math.floor(gu);
        double cv = Math.floor(gv);
        double jx = 0.25 + 0.5 * AMath.hash3(cu, cv, seed);
        double jy = 0.25 + 0.5 * AMath.hash3(cu, cv, seed + 11);
        double r = (radius / cell) * (0.6 + 0.6 * AMath.hash3(cu, cv, seed + 23));
        double dx = gu - cu - jx;
        double dy = gv - cv - jy;
        return dx * dx + dy * dy < r * r;
    }
}

package com.gathin.tvhome.ascii;

/** Port of Pen in renderer.ts: what animals draw with. One per grid, reused every frame. */
public final class Pen {
    /** The sampler's output; preset to defaults before every sample. */
    public final Ink ink = new Ink();
    /** Flock time of the frame being drawn. */
    public double t;
    /** The TV always uses the dark theme. */
    public final String theme = "dark";

    private final AsciiGrid r;
    private final double[] a = new double[2];
    private final double[] b = new double[2];

    Pen(AsciiGrid r) {
        this.r = r;
    }

    public double cw() {
        return r.cw;
    }

    public double ch() {
        return r.ch;
    }

    public int cols() {
        return r.cols;
    }

    public int rows() {
        return r.rows;
    }

    /** A colour that reads against the background: pale on the dark ink. */
    public double[] contrast() {
        return AsciiGrid.INK;
    }

    /** Body coordinates to screen px, into out[0] (x) and out[1] (y). */
    public double[] toWorld(Pose p, double u, double v, double[] out) {
        double c = Math.cos(p.heading);
        double s = Math.sin(p.heading);
        out[0] = p.x + (u * c - v * s) * p.span;
        out[1] = p.y + (u * s + v * c) * p.span;
        return out;
    }

    /** Default fade for something at this depth. */
    public double fogFor(double depth) {
        return (1 - depth) * r.tuning.fog;
    }

    /** Rasterise a silhouette within reach (fraction of span) of the pose. */
    public void shape(Pose p, double reach, Sampler sample) {
        r.shape(p, reach, sample, 1, 0, Double.NaN);
    }

    /**
     * shape(p, reach, sample, { light, tilt, fog }). light: brightness multiplier (default 1); tilt: extra phase
     * for the shimmer roll (default 0); fog: how far this fades into the background, NaN for the default by depth.
     */
    public void shape(Pose p, double reach, Sampler sample, double light, double tilt, double fog) {
        r.shape(p, reach, sample, light, tilt, fog);
    }

    /** A line of glyphs from (x0, y0) to (x1, y1) px, faded by depth, optionally ending in tip (null for none). */
    public void line(double x0, double y0, double x1, double y1, double[] color, double depth, String tip) {
        r.line(x0, y0, x1, y1, color, fogFor(depth), tip);
    }

    public void line(double x0, double y0, double x1, double y1, double[] color, double depth) {
        r.line(x0, y0, x1, y1, color, fogFor(depth), null);
    }

    /** The same with an explicit fog instead of the one for its depth. */
    public void line(double x0, double y0, double x1, double y1, double[] color, double depth, String tip, double fog) {
        r.line(x0, y0, x1, y1, color, fog, tip);
    }

    /** A line in body coordinates. */
    public void lineBody(Pose p, double u0, double v0, double u1, double v1, double[] color, String tip) {
        toWorld(p, u0, v0, a);
        toWorld(p, u1, v1, b);
        r.line(a[0], a[1], b[0], b[1], color, fogFor(p.depth), tip);
    }

    public void lineBody(Pose p, double u0, double v0, double u1, double v1, double[] color) {
        lineBody(p, u0, v0, u1, v1, color, null);
    }

    /** A single glyph at (x, y) px, faded by depth. Soft dots never cover solid cells. */
    public void dot(double x, double y, String glyph, double[] color, double depth) {
        r.dot(x, y, glyph, color, fogFor(depth));
    }

    /** The same with an explicit fog. */
    public void dot(double x, double y, String glyph, double[] color, double depth, double fog) {
        r.dot(x, y, glyph, color, fog);
    }

    /** Exactly one solid cell at (x, y) px: a block of fill with glyph in color, over whatever is there. */
    public void spot(double x, double y, String glyph, double[] color, double[] fill, double depth) {
        r.spot(x, y, glyph, color, fill, fogFor(depth));
    }

    /** Leave something in the trail at (x, y), scaled by the trail knob. */
    public void shed(double x, double y, double amount) {
        r.shed(x, y, amount);
    }

    /** Cell index under (x, y), or -1 off screen. */
    public int cellIndex(double x, double y) {
        return r.cellIndex(x, y);
    }

    /** The line glyph closest to a screen-space angle. */
    public static String lineGlyph(double angle) {
        double a = angle % Math.PI;
        if (a < 0) a += Math.PI;
        return a < Math.PI / 8 || a >= (7 * Math.PI) / 8 ? "-" : a < (3 * Math.PI) / 8 ? "\\"
                : a < (5 * Math.PI) / 8 ? "|" : "/";
    }
}

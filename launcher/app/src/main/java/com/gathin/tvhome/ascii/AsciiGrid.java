package com.gathin.tvhome.ascii;

import java.util.Arrays;

/**
 * The cell logic of ascii-animals src/renderer.ts (AsciiRenderer), without the drawing: a grid of ASCII cells
 * that a Flock is rasterised into every frame, plus the trail field. {@link AsciiAnimalsView} paints the result.
 * Dark theme only. Plain Java, no Android.
 */
public final class AsciiGrid {
    public static final int ALPHA_LEVELS = 6;
    public static final double[] BACKGROUND = {6, 7, 12};
    /** Grid line colour and opacity at tuning.grid = 1. */
    public static final double[] GRID = {255, 255, 255, 0.055};
    public static final double TRAIL_ALPHA = 0.85;
    static final double[] INK = {150, 160, 200};

    private static final String SCALE_RAMP = ".:;+=*%#";
    private static final String DARK_GLYPHS = "#%@";
    private static final String SPOT_GLYPHS = "o*";
    private static final String BODY_GLYPHS = "8#@";
    private static final String GLOW_RAMP = ".:+*";
    private static final String EDGE_HEAVY = ":;";
    private static final String EDGE_LIGHT = ".',`";
    private static final int[] SPARKLES = {0xFFFFFF, 0xE4F6FF, 0xC8F4FF};

    public static final byte CELL_NONE = 0;
    /** A block of colour with a glyph on top. */
    public static final byte CELL_SOLID = 1;
    /** A bare glyph: soft edges, glows, lines, dots. */
    public static final byte CELL_SOFT = 2;

    private static final double[] SUBSAMPLES = {0.25, 0.3, 0.75, 0.3, 0.25, 0.72, 0.75, 0.72};

    public double cw = 12, ch = 18;
    public int cols, rows;
    public double width, height;
    public Tuning tuning;
    public final Pen pen = new Pen(this);

    public float[] energy = new float[0];
    private float[] energyTmp = new float[0];
    public byte[] cellFlag = new byte[0];
    /** Block colour, packed 0xRRGGBB. */
    public int[] cellFill = new int[0];
    /** Glyph colour, packed 0xRRGGBB. */
    public int[] cellColor = new int[0];
    public char[] cellGlyph = new char[0];

    /** Trail colour (hue-turned), packed 0xRRGGBB; alpha per level is TRAIL_ALPHA * (level + 1) / ALPHA_LEVELS. */
    public int trailColor;
    private double[] trailBase = INK;
    private final double[] hue = new double[9];
    private boolean hueOn;
    private final double[] turned = new double[3];

    public AsciiGrid(Tuning tuning) {
        this.tuning = tuning;
        restyle();
    }

    /** Switch to another animal and its tuning; the old trail washes away. */
    public void setAnimal(Animal<?> animal, Tuning tuning) {
        this.tuning = tuning;
        this.trailBase = animal.trail != null ? animal.trail : INK;
        Arrays.fill(energy, 0);
        restyle();
    }

    /** Rebuild cached colours after an animal or hue change. */
    public void restyle() {
        double deg = tuning.hueShift;
        double a = Math.cos(deg * Math.PI / 180);
        double s = Math.sin(deg * Math.PI / 180);
        double[] m = {
            0.213 + a * 0.787 - s * 0.213, 0.715 - a * 0.715 - s * 0.715, 0.072 - a * 0.072 + s * 0.928,
            0.213 - a * 0.213 + s * 0.143, 0.715 + a * 0.285 + s * 0.14, 0.072 - a * 0.072 - s * 0.283,
            0.213 - a * 0.213 - s * 0.787, 0.715 - a * 0.715 + s * 0.715, 0.072 + a * 0.928 + s * 0.072,
        };
        System.arraycopy(m, 0, hue, 0, 9);
        hueOn = deg % 360 != 0;
        turn(trailBase[0], trailBase[1], trailBase[2]);
        trailColor = pack(turned[0], turned[1], turned[2]);
    }

    /** Apply the hue shift to a colour, into turned[]. */
    private void turn(double r, double g, double b) {
        if (!hueOn) {
            turned[0] = r;
            turned[1] = g;
            turned[2] = b;
            return;
        }
        double[] m = hue;
        turned[0] = m[0] * r + m[1] * g + m[2] * b;
        turned[1] = m[3] * r + m[4] * g + m[5] * b;
        turned[2] = m[6] * r + m[7] * g + m[8] * b;
    }

    private static int clampByte(double v) {
        long r = Math.round(v);
        return r < 0 ? 0 : r > 255 ? 255 : (int) r;
    }

    public static int pack(double r, double g, double b) {
        return (clampByte(r) << 16) | (clampByte(g) << 8) | clampByte(b);
    }

    /** Blend a colour toward the background by f (0 colour, 1 background), packed 0xRRGGBB. */
    private static int fade(double r, double g, double b, double f) {
        double br = BACKGROUND[0], bg = BACKGROUND[1], bb = BACKGROUND[2];
        return pack(r + (br - r) * f, g + (bg - g) * f, b + (bb - b) * f);
    }

    /** Lay the grid out for a screen of width x height px. */
    public void resize(double width, double height) {
        int prevCols = cols, prevRows = rows;
        this.width = width;
        this.height = height;
        cw = AMath.clamp(Math.round(width / 124 / tuning.detail), 5, 24);
        ch = Math.round(cw * 1.45);
        cols = (int) Math.ceil(width / cw);
        rows = (int) Math.ceil(height / ch);
        int n = cols * rows;
        if (cols != prevCols || rows != prevRows) {
            energy = new float[n];
            energyTmp = new float[n];
            cellFlag = new byte[n];
            cellFill = new int[n];
            cellColor = new int[n];
            cellGlyph = new char[n];
        }
    }

    /** Free the arrays (memory pressure); the next resize() makes them again. */
    public void release() {
        cols = 0;
        rows = 0;
        energy = energyTmp = new float[0];
        cellFlag = new byte[0];
        cellFill = cellColor = new int[0];
        cellGlyph = new char[0];
    }

    /** render() without the painting: stamp events, let the trail drift, and rasterise the animals into cells. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void frame(Flock flock, double dt) {
        if (cols == 0) return;
        pen.t = flock.time;
        for (Object o : flock.events) {
            Flock.WorldEvent e = (Flock.WorldEvent) o;
            stamp(e.x, e.y, e.radius, e.amount);
        }
        flock.events.clear();
        diffuse(dt);
        Arrays.fill(cellFlag, CELL_NONE);
        Animal animal = flock.animal;
        if (animal.drawsAll()) {
            animal.drawAll(flock, pen);
        } else {
            for (int i = 0; i < flock.creatures.size(); i++) {
                animal.draw((Creature) flock.creatures.get(i), pen, flock);
            }
        }
    }

    // trail

    public int cellIndex(double x, double y) {
        int c = (int) Math.floor(x / cw);
        int r = (int) Math.floor(y / ch);
        if (c < 0 || r < 0 || c >= cols || r >= rows) return -1;
        return r * cols + c;
    }

    public void shed(double x, double y, double amount) {
        int i = cellIndex(x, y);
        if (i >= 0) energy[i] = (float) Math.min(1.6, energy[i] + amount * tuning.trail);
    }

    private void stamp(double x, double y, int radius, double amount) {
        float[] field = energy;
        int c0 = (int) Math.floor(x / cw);
        int r0 = (int) Math.floor(y / ch);
        for (int r = r0 - radius; r <= r0 + radius; r++) {
            for (int c = c0 - radius; c <= c0 + radius; c++) {
                if (c < 0 || r < 0 || c >= cols || r >= rows) continue;
                double d = Math.hypot(c - c0, (r - r0) * 1.4);
                if (d > radius + 0.5) continue;
                int i = r * cols + c;
                field[i] = (float) Math.min(1.6, field[i] + amount * (1 - d / (radius + 1)));
            }
        }
    }

    /** Blur and decay so the trail drifts outward and fades. */
    private void diffuse(double dt) {
        float[] e = energy, tmp = energyTmp;
        float decay = (float) Math.exp(-dt * tuning.trailFade);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i = r * cols + c;
                float l = c > 0 ? e[i - 1] : e[i];
                float rt = c < cols - 1 ? e[i + 1] : e[i];
                float u = r > 0 ? e[i - cols] : e[i];
                float d = r < rows - 1 ? e[i + cols] : e[i];
                tmp[i] = (e[i] * 4 + l + rt + u + d) * 0.125f * decay;
            }
        }
        energy = tmp;
        energyTmp = e;
    }

    // drawing into cells

    private boolean probe(Sampler sample, Pose p, double hc, double hs, double px, double py) {
        Ink ink = pen.ink;
        double dx = px - p.x;
        double dy = py - p.y;
        double u = (dx * hc + dy * hs) / p.span;
        double v = (dy * hc - dx * hs) / p.span;
        ink.glyphs = null;
        ink.au = u;
        ink.av = v;
        return sample.sample(u, v, px, py);
    }

    /**
     * Supersample every cell within reach of the pose. Fully covered cells become solid (or glyph-only, for
     * glows); partial cells become soft edge glyphs, which never cover anything solid. fog NaN: by depth.
     */
    void shape(Pose p, double reach, Sampler sample, double light, double tilt, double fog) {
        if (Double.isNaN(fog)) fog = (1 - p.depth) * tuning.fog;
        if (p.span <= 0) return;
        Ink ink = pen.ink;
        double t = pen.t;
        double shimmer = tuning.shimmer, sparkle = tuning.sparkle;
        double S = p.span;
        double hc = Math.cos(p.heading);
        double hs = Math.sin(p.heading);
        double R = reach * S;
        int c0 = Math.max(0, (int) Math.floor((p.x - R) / cw));
        int c1 = Math.min(cols - 1, (int) Math.floor((p.x + R) / cw));
        int r0 = Math.max(0, (int) Math.floor((p.y - R) / ch));
        int r1 = Math.min(rows - 1, (int) Math.floor((p.y + R) / ch));
        int tick = (int) Math.floor(t * 6);

        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                double x = c * cw;
                double y = r * ch;
                boolean center = probe(sample, p, hc, hs, x + cw / 2, y + ch / 2);
                int hits = center ? 1 : 0;
                double ir = ink.r, ig = ink.g, ib = ink.b;
                int kind = ink.kind;
                String glyphs = ink.glyphs;
                double au = ink.au, av = ink.av;
                for (int k = 0; k < SUBSAMPLES.length; k += 2) {
                    if (probe(sample, p, hc, hs, x + SUBSAMPLES[k] * cw, y + SUBSAMPLES[k + 1] * ch)) {
                        if (hits == 0) {
                            ir = ink.r;
                            ig = ink.g;
                            ib = ink.b;
                        }
                        hits++;
                    }
                }
                if (hits == 0) continue;
                double coverage = hits / 5.0;
                int i = r * cols + c;
                boolean solid = center && coverage >= 0.6;
                if (!solid && cellFlag[i] == CELL_SOLID) continue;

                turn(ir, ig, ib);
                ir = turned[0];
                ig = turned[1];
                ib = turned[2];
                double kk = light * (1 + (AMath.hash3(c, r, tick) - 0.5) * 0.06);
                if (solid && kind == Ink.SHIMMER) {
                    kk *= 1 - 0.4 * shimmer + shimmer * Math.sin(t * 2.2 + au * 9 + av * 6 + tilt + p.heading * 2);
                }
                ir *= kk;
                ig *= kk;
                ib *= kk;
                // Body-anchored key so each glyph rides along with the animal.
                long key = AMath.toUint32(AMath.hash3(Math.round((au * S) / (cw * 0.9)),
                        Math.round((av * S) / (cw * 0.9)), p.id) * 4294967295.0);

                if (!solid) {
                    String set = coverage >= 0.5 ? EDGE_HEAVY : EDGE_LIGHT;
                    cellFlag[i] = CELL_SOFT;
                    cellColor[i] = fade(ir, ig, ib, Math.min(1, fog + 0.55 - 0.45 * coverage));
                    cellGlyph[i] = set.charAt((int) ((key >>> 8) % set.length()));
                    continue;
                }

                if (kind == Ink.GLOW) {
                    if (cellFlag[i] == CELL_SOLID) continue;
                    double lum = (0.3 * ir + 0.59 * ig + 0.11 * ib) / 255;
                    String set = glyphs != null ? glyphs : GLOW_RAMP;
                    cellFlag[i] = CELL_SOFT;
                    cellColor[i] = fade(ir, ig, ib, fog);
                    cellGlyph[i] = set.charAt(glyphs != null ? (int) ((key >>> 8) % set.length())
                            : AMath.clamp((int) Math.floor(lum * set.length()), 0, set.length() - 1));
                    continue;
                }

                cellFlag[i] = CELL_SOLID;
                double roll = AMath.hash3(key, Math.floor(t * 2.4 + (key % 997) / 97.0), 7);
                if (kind == Ink.GLASS) {
                    cellFill[i] = fade(ir, ig, ib, Math.min(1, 0.62 + fog * 0.38));
                    String set = glyphs != null ? glyphs : SCALE_RAMP;
                    cellColor[i] = fade(ir, ig, ib, fog);
                    cellGlyph[i] = set.charAt((int) ((key >>> 8) % set.length()));
                    if (roll < sparkle) cellColor[i] = SPARKLES[(int) (key % SPARKLES.length)];
                    continue;
                }
                if (kind == Ink.LIT) {
                    cellFill[i] = fade(ir * 0.58, ig * 0.58, ib * 0.58, fog);
                    String set = glyphs != null ? glyphs : ".:*";
                    cellColor[i] = roll < sparkle ? SPARKLES[(int) (key % SPARKLES.length)]
                            : fade(Math.min(255, ir * 1.05 + 50), Math.min(255, ig * 1.05 + 50),
                                    Math.min(255, ib * 1.05 + 50), fog);
                    cellGlyph[i] = set.charAt((int) ((key >>> 8) % set.length()));
                    continue;
                }
                int fill = fade(ir, ig, ib, fog);
                cellFill[i] = fill;
                int fr = fill >>> 16, fg = (fill >>> 8) & 255, fb = fill & 255;
                String set;
                double gr, gg, gb;
                if (kind == Ink.BODY) {
                    set = glyphs != null ? glyphs : BODY_GLYPHS;
                    gr = fr * 1.6 + 44;
                    gg = fg * 1.6 + 40;
                    gb = fb * 1.6 + 36;
                } else if (kind == Ink.DARK) {
                    set = glyphs != null ? glyphs : DARK_GLYPHS;
                    gr = fr * 1.7 + 34;
                    gg = fg * 1.7 + 30;
                    gb = fb * 1.7 + 30;
                } else if (kind == Ink.SPOT) {
                    set = glyphs != null ? glyphs : SPOT_GLYPHS;
                    gr = fr * 0.4;
                    gg = fg * 0.4;
                    gb = fb * 0.4;
                } else {
                    set = glyphs != null ? glyphs : SCALE_RAMP;
                    gr = fr * 0.42;
                    gg = fg * 0.42;
                    gb = fb * 0.42;
                }
                char glyph;
                if (glyphs != null || (kind != Ink.SCALE && kind != Ink.SHIMMER)) {
                    glyph = set.charAt((int) ((key >>> 8) % set.length()));
                } else {
                    // Sparse glyphs on bright skin, dense in shadow.
                    double lum = (0.3 * fr + 0.59 * fg + 0.11 * fb) / 255;
                    int jitter = (int) ((key >>> 4) % 3) - 1;
                    glyph = set.charAt(AMath.clamp((int) Math.floor((1 - lum) * (set.length() - 0.01)) + jitter, 0,
                            set.length() - 1));
                }
                cellGlyph[i] = glyph;
                double glints = kind == Ink.SHIMMER ? sparkle : kind == Ink.SCALE ? sparkle / 3 : 0;
                cellColor[i] = roll < glints ? SPARKLES[(int) (key % SPARKLES.length)] : pack(gr, gg, gb);
            }
        }
    }

    void line(double x0, double y0, double x1, double y1, double[] color, double fog, String tip) {
        turn(color[0], color[1], color[2]);
        int packed = fade(turned[0], turned[1], turned[2], fog);
        char glyph = Pen.lineGlyph(Math.atan2(y1 - y0, x1 - x0)).charAt(0);
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(x1 - x0, y1 - y0) / (cw * 0.5)));
        int last = -1;
        for (int s = 1; s <= steps; s++) {
            int i = cellIndex(x0 + ((x1 - x0) * s) / steps, y0 + ((y1 - y0) * s) / steps);
            if (i < 0 || cellFlag[i] == CELL_SOLID) continue;
            cellFlag[i] = CELL_SOFT;
            cellColor[i] = packed;
            cellGlyph[i] = glyph;
            last = i;
        }
        if (tip != null && !tip.isEmpty() && last >= 0) cellGlyph[last] = tip.charAt(0);
    }

    void dot(double x, double y, String glyph, double[] color, double fog) {
        int i = cellIndex(x, y);
        if (i < 0 || cellFlag[i] == CELL_SOLID) return;
        turn(color[0], color[1], color[2]);
        cellFlag[i] = CELL_SOFT;
        cellColor[i] = fade(turned[0], turned[1], turned[2], fog);
        cellGlyph[i] = glyph.charAt(0);
    }

    void spot(double x, double y, String glyph, double[] color, double[] fill, double fog) {
        int i = cellIndex(x, y);
        if (i < 0) return;
        turn(fill[0], fill[1], fill[2]);
        int f = fade(turned[0], turned[1], turned[2], fog);
        turn(color[0], color[1], color[2]);
        cellFlag[i] = CELL_SOLID;
        cellFill[i] = f;
        cellColor[i] = fade(turned[0], turned[1], turned[2], fog);
        cellGlyph[i] = glyph.charAt(0);
    }
}

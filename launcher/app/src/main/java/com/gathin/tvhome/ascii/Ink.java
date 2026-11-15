package com.gathin.tvhome.ascii;

/**
 * Port of ascii-animals src/ink.ts. What a sampled point is made of: shape functions write an Ink, the pen
 * turns it into a cell (a block of colour with a glyph on top, or a bare glyph). Colours are double[3] RGB.
 */
public final class Ink {
    /** Ordinary skin, scales or feathers: a block of colour with a sparse-to-dense glyph. */
    public static final int SCALE = 0;
    /** Dark veins, margins, patches: a block with a dense glyph. */
    public static final int DARK = 1;
    /** Small bright dots: margin spots, eyes, pupils. */
    public static final int SPOT = 2;
    /** Iridescent: brightness rolls across it as the animal turns, and it sparkles more. */
    public static final int SHIMMER = 3;
    /** See-through: a faint block that mostly lets the background show, with a glyph in full colour. */
    public static final int GLASS = 4;
    /** Light without substance: a bare glyph, no block. */
    public static final int GLOW = 5;
    /** Fuzzy bodies: a block with a heavy glyph. */
    public static final int BODY = 6;
    /** Lit from within: a deep block of the colour with a bright, nearly white glyph. */
    public static final int LIT = 7;

    public double r, g, b;
    public int kind;
    /** Glyphs to use instead of the kind's own set; null for the default. */
    public String glyphs;
    /** Body-anchored coordinates (fractions of span) that seed the glyph texture and shimmer. */
    public double au, av;

    /** ink(out, r, g, b, kind, glyphs) in the original. Returns true so a sampler can return it on a hit. */
    public static boolean ink(Ink out, double r, double g, double b, int kind, String glyphs) {
        out.r = r;
        out.g = g;
        out.b = b;
        out.kind = kind;
        out.glyphs = glyphs;
        return true;
    }

    public static boolean ink(Ink out, double r, double g, double b, int kind) {
        return ink(out, r, g, b, kind, null);
    }

    public static boolean ink(Ink out, double r, double g, double b) {
        return ink(out, r, g, b, SCALE, null);
    }

    public static boolean ink(Ink out, double[] rgb, int kind, String glyphs) {
        return ink(out, rgb[0], rgb[1], rgb[2], kind, glyphs);
    }

    public static boolean ink(Ink out, double[] rgb, int kind) {
        return ink(out, rgb[0], rgb[1], rgb[2], kind, null);
    }

    public static double[] rgb(double r, double g, double b) {
        return new double[] {r, g, b};
    }
}

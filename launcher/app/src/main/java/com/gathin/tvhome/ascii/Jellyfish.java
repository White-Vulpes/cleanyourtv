package com.gathin.tvhome.ascii;

/**
 * Port of ascii-animals src/animals/jellyfish.ts: bioluminescent jellyfish pulsing along the course.
 *
 * Swimming is pulse-and-coast: the bell squeezes (narrower and longer), which shoves the jelly forward, then it
 * relaxes and coasts on drag until the next pulse. Each pulse also lights it up: the glow surges as the bell
 * snaps shut and then eases out through the rest of the stroke.
 *
 * <pre>
 *   bell       lit from within: brightest at the rim and the crown, with
 *              radial ribs and a dusting of white dots over the top
 *   halo       a faint glow spilling into the water around it
 *   rim        a row of small scalloped lobes all along the margin
 *   oral arms  long frilly ribbons from the middle, twisting as they trail,
 *              with bright ruffled edges around a dim core
 *   tentacles  hair-thin, faint threads, roots toward the middle
 *   snow       glowing specks drifting slowly through the water
 * </pre>
 */
public final class Jellyfish extends Animal<Jellyfish.J> {

    public static final class J extends Creature {
        /** Pulse phase, radians. */
        public double phase;
        /** 0 relaxed to 1 fully squeezed. */
        public double squeeze;
        /** Smoothed turn rate, so the arms swing out on the outside of a turn. */
        public double turn;
    }

    // Magenta: every animal has its own colour, and blue belongs to the butterflies.
    private static final double[] CROWN = {255, 168, 230};
    private static final double[] BODY = {232, 56, 182};
    private static final double[] RIM = {255, 200, 240};
    private static final double[] LOBES = {255, 214, 244};
    private static final double[] DOTS = {255, 240, 250};
    private static final double[] FRILL = {255, 214, 240};
    private static final double[] CORE = {204, 56, 172};
    private static final double[] TENTACLE = {232, 100, 204};
    private static final double[] SNOW = {255, 170, 226};

    /** "{}~§" split into single glyphs, so the frill needs no substring per dot. */
    private static final String[] FRILL_GLYPHS = {"{", "}", "~", "\u00a7"};

    /** Screen area per speck of marine snow, px squared. */
    private static final double SNOW_AREA = 6000;

    private static final double U0 = -0.04;

    private final double[] at = new double[2];
    private final double[] bt = new double[2];
    private final Pose pose = new Pose();

    // What the two samplers read, set before each pen.shape() call (shape runs them synchronously).
    private double sa, sb, sh;
    private int sid;
    private Ink out;

    /** The faint halo where the glow spills into the water. */
    private final Sampler halo = (u, v, px, py) -> {
        double r = Math.hypot((u - U0) / (sa * sh), v / (sb * sh));
        return r < 1 && u > U0 - 0.08 && Ink.ink(out, RIM, Ink.GLOW, ".");
    };

    /** The bell, lit from within. */
    private final Sampler bell = (u, v, px, py) -> {
        double du = (u - U0) / sa;
        double dv = v / sb;
        double r2 = du * du + dv * dv;
        double margin = U0 - 0.04 * dv * dv;
        if (r2 > 1 || u < margin) {
            // A row of scalloped lobes hanging just off the margin.
            if (Math.abs(dv) > 1 || u > margin || u < margin - 0.04) return false;
            double lobe = 0.04 * Math.sin(AMath.frac((dv + 1) * 5.5) * Math.PI);
            return u > margin - lobe && Ink.ink(out, LOBES, Ink.LIT, "o");
        }
        double r = Math.sqrt(r2);
        if (r > 0.88) return Ink.ink(out, RIM, Ink.LIT, ":;");
        double th = Math.atan2(dv, du);
        // A dusting of white dots over the top of the dome.
        if (du > 0.3) {
            double gu = Math.round(du * 11);
            double gv = Math.round(dv * 13);
            boolean near = Math.hypot(du * 11 - gu, dv * 13 - gv) < 0.32;
            if (near && AMath.hash3(gu, gv, sid) > 0.45) return Ink.ink(out, DOTS, Ink.LIT, ".'");
        }
        // Radial ribs, brighter toward the rim.
        if (r > 0.35 && AMath.frac((th / Math.PI) * 6) < 0.1) return Ink.ink(out, RIM, Ink.LIT, "|");
        // Glow: brightest at the crown and toward the rim, a little dimmer in the middle.
        double cd = AMath.clamp(du, 0, 1);
        double glow = 0.75 + 0.25 * Math.max(cd, r * r);
        double f = cd * 0.8;
        double cr = BODY[0] + (CROWN[0] - BODY[0]) * f;
        double cg = BODY[1] + (CROWN[1] - BODY[1]) * f;
        double cb = BODY[2] + (CROWN[2] - BODY[2]) * f;
        return Ink.ink(out, cr * glow, cg * glow, cb * glow, Ink.LIT, " .");
    };

    public Jellyfish() {
        super("jellyfish", "Jellyfish", 95000, 200, 1.6, 55, new double[] {255, 130, 214});
    }

    @Override
    public void defaults(Tuning t) {
        t.speed = 1.05;
        t.sway = 0.04;
        t.spread = 0.08;
        t.farthest = 0.3;
        t.farBias = 1.4;
        t.grip = 1.4;
        t.trail = 0.8;
        t.trailFade = 0.6;
        t.sparkle = 0.1;
        t.put("pulseRate", 1);
        t.put("pulsePower", 1);
        t.put("pulseGlow", 1);
        t.put("tentacles", 1);
    }

    @Override
    public J newCreature() {
        return new J();
    }

    @Override
    public void init(J j, Flock<J> flock) {
        j.phase = AMath.rand(0, AMath.TAU);
        j.squeeze = 0;
        j.turn = 0;
    }

    @Override
    public void update(J j, Flock<J> flock, double dt) {
        Tuning k = flock.tuning;
        j.phase += dt * AMath.TAU * 0.6 * k.get("pulseRate") * (0.9 + 0.2 * j.roll);
        double p = AMath.frac(j.phase / AMath.TAU);
        // A quick squeeze, then a slow relax.
        j.squeeze = p < 0.22 ? AMath.smoothstep(0, 0.22, p) : 1 - AMath.smoothstep(0.22, 1, p);

        // Lazily turn toward the course; jellies can't steer hard.
        double want = flock.heading() + j.bias * k.spread;
        double before = j.heading;
        j.heading += AMath.clamp(AMath.angleDiff(j.heading, want), -0.6 * k.grip * dt, 0.6 * k.grip * dt);
        j.turn += (AMath.angleDiff(before, j.heading) / dt - j.turn) * Math.min(1, dt * 3);

        double cruise = flock.cruise(j);
        double[] acc = flock.begin();
        // Thrust only while squeezing; drag the rest of the time, so it coasts.
        double thrust = p < 0.22 ? 6.2 * cruise * k.get("pulsePower") : 0;
        acc[0] += Math.cos(j.heading) * thrust - j.vx * 1.4;
        acc[1] += Math.sin(j.heading) * thrust - j.vy * 1.4;
        flock.separate(j, k.separation, 0.5);
        flock.move(j, dt, cruise * 3);
        if (flock.passed(j, j.span * 1.4)) flock.reenter(j);
    }

    @Override
    public boolean startle(J j, double dx, double dy, double strength, Flock<J> flock) {
        // A hard pulse right now that surges it ahead on the course it's already on (no turning).
        j.phase = Math.floor(j.phase / AMath.TAU) * AMath.TAU + AMath.TAU;
        double boost = 260 * strength * j.depth;
        j.vx += Math.cos(j.heading) * boost;
        j.vy += Math.sin(j.heading) * boost;
        j.startledUntil = flock.time + 0.8;
        return true;
    }

    @Override
    public boolean drawsAll() {
        return true;
    }

    @Override
    public void drawAll(Flock<J> flock, Pen pen) {
        drawSnow(flock, pen);
        double len = flock.tuning.get("tentacles");
        double pulseGlowK = flock.tuning.get("pulseGlow");
        for (int i = 0, n = flock.creatures.size(); i < n; i++) {
            drawJelly(flock.creatures.get(i), pen, len, pulseGlowK);
        }
    }

    /** The glow's surge on each pulse: quick to light as the bell snaps shut, then easing out. */
    private static double pulseGlow(J j) {
        double p = AMath.frac(j.phase / AMath.TAU);
        return p < 0.16 ? AMath.smoothstep(0, 0.16, p) : Math.exp(-(p - 0.16) * 4.5);
    }

    private void drawJelly(J j, Pen pen, double len, double pulseGlowK) {
        double S = j.span;
        double sq = j.squeeze;
        double t = pen.t;
        Pose pose = this.pose.set(j.x, j.y, j.heading, S, j.depth, j.id);
        double base = pen.fogFor(j.depth);
        double a = 0.32 * (1 + 0.14 * sq);
        double b = 0.3 * (1 - 0.2 * sq);
        double u0 = U0;
        double sway = AMath.clamp(-j.turn * 0.5, -0.5, 0.5);
        double flash = pulseGlow(j) * pulseGlowK;

        // Hair-thin tentacles: faint threads, roots toward the middle of the rim.
        for (int n = 0; n < 8; n++) {
            double v0 = (n / 7.0 - 0.5) * 2 * b * 0.6;
            double pu = u0 - 0.02;
            double pv = v0;
            int segs = 14;
            double length = 1.5 * len * (0.75 + 0.35 * AMath.hash3(j.id, n, 4));
            for (int i = 1; i <= segs; i++) {
                double f = (double) i / segs;
                double u = u0 - 0.02 - f * length * (1 - 0.12 * sq);
                double v = v0 * (1 - 0.3 * f) + 0.04 * Math.sin(j.phase - f * 3.5 + n * 1.3) * f + sway * f * f * 0.4;
                pen.toWorld(pose, pu, pv, at);
                pen.toWorld(pose, u, v, bt);
                pen.line(at[0], at[1], bt[0], bt[1], TENTACLE, j.depth, null, Math.min(0.95, base + 0.45 + 0.35 * f));
                pu = u;
                pv = v;
            }
        }

        // Oral arms: frilly ribbons that twist as they trail, bright ruffled edges around a dim core.
        for (int n = 0; n < 4; n++) {
            double v0 = (n / 3.0 - 0.5) * 0.11;
            int segs = 26;
            double length = 1.05 * len * (0.8 + 0.3 * AMath.hash3(j.id, n, 7));
            for (int i = 1; i <= segs; i++) {
                double f = (double) i / segs;
                double u = u0 - f * length;
                double v = v0 * (1 - 0.4 * f) + 0.05 * Math.sin(j.phase * 0.9 - f * 4.5 + n * 1.7) * f + sway * f * 0.25;
                // The twist: the ribbon's width swings through zero and back as it spirals.
                double w = 0.055 * (1 - 0.5 * f) * Math.cos(f * 9 + n * 1.3 + t * 0.6);
                double fog = Math.min(0.95, base + 0.3 * f);
                pen.toWorld(pose, u, v, at);
                pen.dot(at[0], at[1], i % 3 != 0 ? ":" : "|", CORE, j.depth, Math.min(0.95, fog + 0.15));
                for (int side = -1; side <= 1; side += 2) {
                    pen.toWorld(pose, u, v + w * side, at);
                    pen.dot(at[0], at[1], FRILL_GLYPHS[(i + n + (side > 0 ? 1 : 0)) % 4], FRILL, j.depth, fog);
                }
            }
            pen.toWorld(pose, u0 - length, v0, at);
            pen.shed(at[0], at[1], 0.015 * j.depth);
        }

        out = pen.ink;
        sa = a;
        sb = b;
        sid = j.id;

        // A faint halo where the glow spills into the water, brighter and wider while the pulse glows.
        sh = 1.3 * (1 + 0.18 * flash);
        pen.shape(pose, 0.32 * sh + 0.1, halo, 1, 0, AMath.clamp(base + 0.55 - 0.3 * flash, 0, 0.95));

        // The bell, lit from within.
        pen.shape(pose, 0.42, bell, (0.92 + 0.18 * sq) * (1 + 0.6 * flash), 0, Double.NaN);
    }

    /** Marine snow: glowing specks that drift slowly up through the water and twinkle. */
    private static void drawSnow(Flock<J> flock, Pen pen) {
        double W = flock.width;
        double H = flock.height;
        long n = Math.round((W * H) / SNOW_AREA);
        double t = pen.t;
        for (int i = 0; i < n; i++) {
            double rise = 3 + 9 * AMath.hash3(i, 3, 1);
            double x = AMath.hash3(i, 1, 1) * W + Math.sin(t * 0.25 + i) * 6;
            double y = (((AMath.hash3(i, 2, 1) * H - t * rise) % H) + H) % H;
            double twinkle = 0.5 + 0.5 * Math.sin(t * (0.8 + 1.6 * AMath.hash3(i, 4, 1)) + i);
            boolean big = AMath.hash3(i, 5, 1) > 0.92;
            pen.dot(x, y, big ? "*" : ".", SNOW, 1, Math.min(0.95, 0.35 + 0.5 * (1 - twinkle) + (big ? 0 : 0.15)));
        }
    }
}

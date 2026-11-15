package com.gathin.tvhome.ascii;

/**
 * Port of ascii-animals src/animals/dragonflies.ts (MIT, drewdi). Emerald hawker dragonflies: hover, dart,
 * cruise. Made for dark mode.
 *
 * Each one hangs in the air with a little bob, then shoots off in a straight dart (roughly along the course, but
 * anywhere within a wide cone). After a dart it either stops dead and hovers again or carries on cruising along
 * the course for a few seconds, gently weaving, before hovering or darting again. Hovers vary a lot: mostly
 * brief, now and then a long one. Darts leave a faint glint.
 *
 * Emerald is its colour (the bands down the body, the thorax, the wing roots), with the markings of a real hawker
 * around it: white eyes with black pupils at the front (only on the nearer, bigger ones), a thorax striped bright
 * and dark green, legs tucked beneath, a long thin dark-green abdomen banded emerald with pale joints between
 * segments; and long slender clear wings held straight out (hindwings broader at the base), each with an amber
 * leading edge, a net of warm white veins, a faint dotted membrane, a green tint at the root, a reddish-brown spot
 * near the tip, and a sunlit glint sweeping along it with every beat. From above, the wingbeat is only a flicker:
 * the wings stay spread.
 */
public final class Dragonflies extends Animal<Dragonflies.D> {

    public static final int HOVER = 0;
    public static final int DART = 1;
    public static final int CRUISE = 2;

    public static final class D extends Creature {
        /** Wingbeat phase. */
        public double phase;
        /** HOVER, DART or CRUISE. */
        public int mode = HOVER;
        /** When the current hover, dart or cruise ends. */
        public double until;
        /** Direction of the current (or next) dart. */
        public double aim;
    }

    /** One wing: root (u, v), angle forward of straight out, length, half-width, and extra breadth near the root. */
    private static final class Wing {
        final double u, v, angle, length, width, broad;
        final double sa, ca;

        Wing(double u, double v, double angle, double length, double width, double broad) {
            this.u = u;
            this.v = v;
            this.angle = angle;
            this.length = length;
            this.width = width;
            this.broad = broad;
            sa = Math.sin(angle);
            ca = Math.cos(angle);
        }
    }

    // Long and slender, held straight out; hindwings broader at the base.
    private static final Wing FORE = new Wing(0.11, 0.02, 0.08, 0.5, 0.062, 0);
    private static final Wing HIND = new Wing(0.06, 0.02, -0.05, 0.48, 0.07, 0.5);
    private static final Wing[] WINGS = {FORE, HIND};

    /** Where the last onWing() hit fell: s along it (0 root to 1 tip) and x across (-1 trailing to 1 leading). */
    private double hitS, hitX;

    private boolean onWing(Wing w, double u, double vw) {
        double du = u - w.u;
        double dv = vw - w.v;
        double sa = w.sa;
        double ca = w.ca;
        double s = (du * sa + dv * ca) / w.length;
        if (s < 0 || s > 1) return false;
        double across = du * ca - dv * sa;
        double half = w.width * Math.pow(Math.sin(Math.PI * (0.1 + 0.9 * s)), 0.45) * (1 + w.broad * (1 - s) * (1 - s));
        if (Math.abs(across) > half) return false;
        hitS = s;
        hitX = across / half;
        return true;
    }

    // Emerald is the defining colour (a cool green, well clear of the frogs' yellow-green);
    // the rest are a hawker's own markings.
    private static final double[] EMERALD = {50, 220, 120};
    private static final double[] EYE_WHITE = {240, 240, 232};
    private static final double[] PUPIL = {10, 10, 12};
    private static final double[] THORAX = {96, 232, 130};
    private static final double[] THORAX_STRIPE = {28, 70, 44};
    private static final double[] LEGS = {40, 60, 44};
    private static final double[] ABDOMEN = {24, 72, 46};
    private static final double[] JOINT = {200, 255, 220};
    private static final double[] TIP = {18, 40, 28};
    private static final double[] COSTA = {236, 168, 70};
    private static final double[] VEIN = {226, 236, 214};
    private static final double[] ROOT = {90, 220, 140};
    private static final double[] STIGMA = {176, 70, 40};
    private static final double[] GLINT = {255, 255, 255};

    // Knobs, read once per step in prepare().
    private double kHover, kCruising, kDart, kWingbeat;

    // Per-draw state the sampler reads (draw runs on one thread, and shape() samples synchronously).
    private final Pose pose = new Pose();
    private final double[] at = new double[2];
    /** Open of the fore and hind pairs, in the order of WINGS. */
    private final double[] opens = new double[2];
    private Ink out;
    private D cur;
    private double drawS, drawMinBody, glintAt, tick;
    private final Sampler sampler = this::sample;

    public Dragonflies() {
        // Big, with few far away, so a wing is several cells across: room for the vein net.
        super("dragonflies", "Dragonfly", 150000, 200, 2.1, 70, Ink.rgb(140, 255, 190));
    }

    @Override
    public void defaults(Tuning t) {
        t.fog = 0.26;
        t.density = 2.1;
        t.sway = 0;
        t.spread = 0;
        t.farthest = 0.32;
        t.farBias = 3;
        t.grip = 5;
        t.separation = 720;
        t.trail = 0.6;
        t.trailFade = 1.2;
        t.sparkle = 0.18;
        t.put("hover", 1.3);
        t.put("cruising", 0.5);
        t.put("dart", 1.5);
        t.put("wingbeat", 1);
    }

    @Override
    public D newCreature() {
        return new D();
    }

    @Override
    public void init(D d, Flock<D> flock) {
        d.phase = AMath.rand(0, AMath.TAU);
        d.mode = HOVER;
        d.until = 0;
        d.aim = 0;
    }

    @Override
    public void enter(D d, Flock<D> flock) {
        d.mode = HOVER;
        d.until = flock.time + AMath.rand(0, 0.6);
        d.aim = flock.course();
    }

    @Override
    public void prepare(Flock<D> flock, double dt) {
        Tuning k = flock.tuning;
        kHover = k.get("hover");
        kCruising = k.get("cruising");
        kDart = k.get("dart");
        kWingbeat = k.get("wingbeat");
    }

    @Override
    public void update(D d, Flock<D> flock, double dt) {
        Tuning k = flock.tuning;
        double t = flock.time;
        double cruise = flock.cruise(d);
        if (t >= d.until) {
            // Hover to dart. Dart to cruise or hover. Cruise to hover, or now and then straight into a dart.
            int from = d.mode;
            int next = from == HOVER ? DART
                    : from == DART ? (AMath.random() < kCruising ? CRUISE : HOVER)
                    : AMath.random() < 0.35 ? DART : HOVER;
            d.mode = next;
            if (next == DART) {
                d.until = t + AMath.rand(0.15, 0.45);
                // Straight out of a cruise, dart on roughly the way it's already heading.
                if (from == CRUISE) d.aim = d.heading + AMath.rand(-0.4, 0.4) * (k.spread + 0.2);
            } else if (next == CRUISE) {
                d.until = t + AMath.rand(1.2, 4.5);
                d.aim = flock.heading() + d.bias * k.spread + AMath.rand(-0.5, 0.5) * k.spread;
            } else {
                // Mostly brief hovers, now and then a long one.
                d.until = t + Math.min(3, Math.max(0.15, -Math.log(1 - AMath.random()) * 0.7)) * kHover;
                // Pick the next dart now, so it can turn to face it while hovering.
                d.aim = flock.heading() + d.bias * k.spread * 0.5 + AMath.rand(-1, 1) * k.spread * 1.6;
            }
        }

        double[] acc = flock.begin();
        if (d.mode == DART) {
            // Drift forward fast enough that hovering plus darting still covers the course.
            flock.steer(d, d.aim, cruise * 4 * kDart, 10);
        } else if (d.mode == CRUISE) {
            // Fly on along the course, the aim weaving gently.
            d.aim += AMath.rand(-1, 1) * dt * 1.2;
            d.aim += (flock.heading() + d.bias * k.spread - d.aim) * Math.min(1, dt * 0.8);
            flock.steer(d, d.aim, cruise * 1.8, 3);
        } else {
            // Hold position with a slow drift and a little bob.
            flock.steer(d, flock.heading(), cruise * 0.25, 4 * k.grip);
            acc[0] += Math.cos(t * 3 + d.id) * 40;
            acc[1] += Math.sin(t * 4.3 + d.id * 2) * 40;
        }
        flock.separate(d);
        flock.move(d, dt, cruise * 4.5 * kDart);
        if (flock.passed(d)) flock.reenter(d);

        // Face the dart; cruising, face the way it's flying; hovering, swing round to face the next dart.
        if (d.mode == CRUISE) flock.face(d, dt, 4);
        else flock.face(d, dt, d.mode == DART ? 14 : 2.5, d.aim);
        d.phase += dt * AMath.TAU * 7 * kWingbeat;
    }

    @Override
    public boolean startle(D d, double dx, double dy, double strength, Flock<D> flock) {
        // A dart straight ahead along its course, whichever side the startle came from.
        d.mode = DART;
        d.aim = flock.heading() + d.bias * flock.tuning.spread;
        d.until = flock.time + 0.25 + 0.3 * strength;
        d.startledUntil = d.until;
        return true;
    }

    private boolean sample(double u, double v, double px, double py) {
        Ink out = this.out;
        double av = Math.abs(v);
        // Thorax, striped on the diagonal.
        double tu = (u - 0.12) / 0.07;
        double tv = av / 0.046;
        if (tu * tu + tv * tv <= 1) {
            return Ink.ink(out, AMath.frac((u + av) * 22) < 0.5 ? THORAX : THORAX_STRIPE, Ink.LIT, "#8");
        }
        // Long thin dark-green abdomen, banded emerald at every segment with a pale joint, darkest at the tip.
        if (u < 0.06 && u > -0.74) {
            double half = Math.max(0.017 - 0.006 * ((0.06 - u) / 0.8), drawMinBody / drawS);
            if (av <= half) {
                if (u < -0.66) return Ink.ink(out, TIP, Ink.DARK);
                double seg = AMath.frac(u * 20);
                if (seg < 0.1) return Ink.ink(out, JOINT, Ink.LIT, "-");
                return seg < 0.45 ? Ink.ink(out, EMERALD, Ink.LIT, "=") : Ink.ink(out, ABDOMEN, Ink.LIT, ":=");
            }
        }
        // Wings: veins only, mostly clear.
        for (int i = 0; i < 2; i++) {
            Wing w = WINGS[i];
            if (!onWing(w, u, av / opens[i])) continue;
            double side = v >= 0 ? 1 : -1;
            double heading = cur.heading;
            // Glyphs that run along the wing, and across it, on screen.
            String along = Pen.lineGlyph(heading + Math.atan2(side * w.ca, w.sa));
            String across = Pen.lineGlyph(heading + Math.atan2(-side * w.sa, w.ca));
            double s = hitS;
            double x = hitX;
            // The reddish-brown spot near the tip, on the leading edge.
            if (s > 0.82 && s < 0.9 && x > 0.35) return Ink.ink(out, STIGMA, Ink.LIT, "#");
            // The leading-edge vein: an amber line.
            if (x > 0.72) return Ink.ink(out, COSTA, Ink.GLOW, along);
            // A sunlit glint sweeping along the wing with each beat.
            if (Math.abs(s - glintAt) < 0.05 && AMath.hash3(Math.round(u * 60), Math.round(v * 60), tick) > 0.45) {
                return Ink.ink(out, GLINT, Ink.GLOW, "*+");
            }
            // The vein net: lengthwise veins and cross veins.
            boolean lengthwise = Math.abs(x + 0.15) < 0.09 || Math.abs(x - 0.35) < 0.07 || x < -0.85 || s > 0.97;
            boolean cross = AMath.frac(s * 9) < 0.09;
            if (lengthwise) return Ink.ink(out, VEIN, Ink.GLOW, cross ? "+" : along);
            if (cross) return Ink.ink(out, VEIN, Ink.GLOW, across);
            // A green tint at the root, then just a faint dotted membrane.
            if (s < 0.18) return Ink.ink(out, ROOT, Ink.GLASS, ".");
            return AMath.hash3(Math.round(u * 50), Math.round(v * 50), cur.id) > 0.6 && Ink.ink(out, VEIN, Ink.GLOW, ".");
        }
        return false;
    }

    @Override
    public void draw(D d, Pen pen, Flock<D> flock) {
        double S = d.span;
        pose.set(d.x, d.y, d.heading, S, d.depth, d.id);
        out = pen.ink;
        cur = d;
        drawS = S;
        // Fore and hind pairs beat out of step, but stay mostly spread.
        double openFore = 0.86 + 0.14 * Math.cos(d.phase);
        double openHind = 0.86 + 0.14 * Math.cos(d.phase + 1.7);
        opens[0] = openFore;
        opens[1] = openHind;
        drawMinBody = pen.cw() * 0.38;
        // Where along the wings the glint is right now: it sweeps root to tip once a beat.
        glintAt = AMath.frac(d.phase / AMath.TAU);
        tick = Math.floor(pen.t * 24);

        // Legs tucked under the thorax, drawn first so the body covers their roots.
        for (int side = -1; side <= 1; side += 2) {
            pen.lineBody(pose, 0.14, 0.03 * side, 0.2, 0.07 * side, LEGS);
            pen.lineBody(pose, 0.1, 0.03 * side, 0.12, 0.08 * side, LEGS);
        }

        pen.shape(pose, 0.74, sampler, 0.92 + 0.08 * openFore, 0, Double.NaN);

        // Eyes: one white cell each with a solid black pupil, so they always show and always match.
        // Only on dragonflies big enough for the two to sit in cells of their own; smaller ones go without.
        if (0.068 * S >= pen.cw()) {
            for (int side = -1; side <= 1; side += 2) {
                pen.toWorld(pose, 0.2, 0.034 * side, at);
                pen.spot(at[0], at[1], "●", PUPIL, EYE_WHITE, d.depth);
            }
        }

        if (d.mode == DART) pen.shed(d.x, d.y, 0.06 * d.depth);
        cur = null;
    }
}

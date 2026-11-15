package com.gathin.tvhome.ascii;

/**
 * Port of ascii-animals src/animals/butterflies.ts (MIT, drewdi). Blue morpho butterflies on migration.
 *
 * Wings are seen from above. Each lobe (forewing, hindwing) is a fan in polar coordinates around its root on the
 * body, so an outline is just a radius per angle, and the paint gets a point in lobe space (t across the fan, r
 * from root to margin): margins are high r, spots repeat across t. Raised wings are foreshortened across the
 * body, so the sampler unfolds v by how open the wings are before testing the outline; that one division is the
 * flap.
 *
 * Flight: hold the course, plus a flutter (a random walk that decays back to zero), a thrust pulse on every
 * downstroke, and now and then a glide with the wings held flat.
 */
public final class Butterflies extends Animal<Butterflies.B> {

    public static final class B extends Creature {
        /** Wingbeat phase. */
        public double phase;
        /** How flat the wings are seen from above: ~0.1 raised together to 1 spread open. */
        public double open = 1;
        /** 0 flapping to 1 gliding. */
        public double glide;
        public double glideUntil;
        public double flutterX, flutterY;
    }

    private static final class Lobe {
        /** Angle range in radians: 0 points straight out sideways, positive sweeps toward the head. */
        final double from, to;
        /** Longest radius, as a fraction of wingspan. */
        final double reach;
        /** Where across [from, to] (0 to 1) the reach peaks. */
        final double peak;
        /** Less than 1 gives a blunt, rounded fan; more than 1 a pointed one. */
        final double round;
        /** Depth of a wavy (scalloped) margin; 0 for a smooth edge. */
        final double scallop;

        Lobe(double from, double to, double reach, double peak, double round, double scallop) {
            this.from = from;
            this.to = to;
            this.reach = reach;
            this.peak = peak;
            this.round = round;
            this.scallop = scallop;
        }
    }

    private static final Lobe FORE = new Lobe(-0.3, 1.0, 0.56, 0.55, 0.5, 0);
    private static final Lobe HIND = new Lobe(-1.45, 0.15, 0.44, 0.48, 0.42, 0.04);
    /** Where each wing pair joins the body, as a fraction of span ahead of the centre. */
    private static final double FORE_ROOT = 0.06;
    private static final double HIND_ROOT = -0.02;
    private static final double[] BODY = {30, 30, 42};
    private static final double FLAP_HZ = 2.6;
    private static final double GLIDE = 0.35;
    private static final double FLUTTER = 1.3;

    /** Lobe radius at angle phi (t is its position across the fan), or 0 outside its fan. */
    private static double lobeReach(Lobe l, double phi, double t) {
        if (t <= 0 || t >= 1) return 0;
        double s = t < l.peak ? (t / l.peak) * 0.5 : 0.5 + ((t - l.peak) / (1 - l.peak)) * 0.5;
        double R = l.reach * Math.pow(Math.sin(Math.PI * s), l.round);
        return l.scallop != 0 ? R * (1 - l.scallop * (0.5 + 0.5 * Math.cos(t * Math.PI * 14))) : R;
    }

    /** Body half-width at u (fraction of span, + toward the head): round head, thorax, tapering abdomen. */
    private static double bodyHalfWidth(double u) {
        if (u > 0.17 || u < -0.32) return 0;
        if (u > 0.11) {
            double d = u - 0.14;
            return Math.sqrt(Math.max(0, 0.03 * 0.03 - d * d));
        }
        if (u > -0.04) return 0.034;
        return 0.03 * (1 - 0.6 * ((-0.04 - u) / 0.28));
    }

    /** Where the last inLobe() hit fell: t across the fan, r from root (0) to margin (1). */
    private double lobeT, lobeR;

    private boolean inLobe(Lobe l, double du, double vw) {
        double phi = Math.atan2(du, vw);
        double t = (phi - l.from) / (l.to - l.from);
        double R = lobeReach(l, phi, t);
        double r = Math.hypot(du, vw);
        if (r > R) return false;
        lobeT = t;
        lobeR = r / R;
        return true;
    }

    // Knobs, read once per step in prepare().
    private double kFlutter, kGlide, kFlapRate;

    // Per-draw state the sampler reads (draw runs on one thread, and shape() samples synchronously).
    private final Pose pose = new Pose();
    private final double[] at = new double[2];
    private Ink ink;
    private double drawS, drawOpen, drawMinBody;
    private final Sampler sampler = this::sample;

    public Butterflies() {
        super("butterflies", "Butterfly", 48000, 200, 1, 90, Ink.rgb(96, 156, 255));
    }

    @Override
    public void defaults(Tuning t) {
        t.density = 1.05;
        t.speed = 1.05;
        t.sway = 0;
        t.spread = 0;
        t.size = 1.25;
        t.farthest = 0.18;
        t.farBias = 1.7;
        t.grip = 5;
        t.separation = 700;
        t.trail = 1.55;
        t.trailFade = 0.15;
        t.put("flutter", 0);
        t.put("glide", 2.75);
        t.put("flapRate", 1);
    }

    @Override
    public B newCreature() {
        return new B();
    }

    @Override
    public void init(B b, Flock<B> flock) {
        b.phase = AMath.rand(0, AMath.TAU);
        b.open = 1;
        b.glide = 0;
        b.glideUntil = 0;
        b.flutterX = 0;
        b.flutterY = 0;
    }

    @Override
    public void enter(B b, Flock<B> flock) {
        b.flutterX = 0;
        b.flutterY = 0;
    }

    @Override
    public void prepare(Flock<B> flock, double dt) {
        Tuning k = flock.tuning;
        kFlutter = k.get("flutter");
        kGlide = k.get("glide");
        kFlapRate = k.get("flapRate");
    }

    @Override
    public void update(B b, Flock<B> flock, double dt) {
        Tuning k = flock.tuning;
        double t = flock.time;
        double cruise = flock.cruise(b);
        double[] acc = flock.begin();
        flock.steer(b, flock.heading() + b.bias * k.spread, cruise, k.grip);

        // Flutter: a random walk that keeps decaying back to zero, so the path zigzags without drifting.
        // Gliding calms it; distance shrinks it.
        double kick = Math.sqrt(dt) * 520 * FLUTTER * kFlutter * (1 - 0.8 * b.glide) * (0.5 + 0.5 * b.depth);
        b.flutterX += -b.flutterX * 3 * dt + (AMath.random() * 2 - 1) * kick;
        b.flutterY += -b.flutterY * 3 * dt + (AMath.random() * 2 - 1) * kick;
        acc[0] += b.flutterX;
        acc[1] += b.flutterY;

        // Every downstroke shoves the butterfly forward a little.
        double down = Math.max(0, -Math.sin(b.phase)) * (1 - b.glide);
        acc[0] += Math.cos(b.heading) * down * cruise * 1.4;
        acc[1] += Math.sin(b.heading) * down * cruise * 1.4;

        flock.separate(b);
        double speed = flock.move(b, dt, cruise * 2.2);
        if (flock.passed(b)) flock.reenter(b);
        if (speed > 12) flock.face(b, dt);

        if (b.glide < 0.5 && t > b.glideUntil && speed > cruise * 0.5 && AMath.random() < GLIDE * kGlide * dt) {
            b.glideUntil = t + AMath.rand(0.5, 1.4);
        }
        b.glide += ((t < b.glideUntil ? 1 : 0) - b.glide) * Math.min(1, dt * 6);
        double urgency = AMath.clamp(speed / (cruise * 2), 0, 1);
        b.phase += dt * AMath.TAU * FLAP_HZ * kFlapRate * (0.8 + 0.6 * urgency) * (1 - 0.9 * b.glide);
        double beat = Math.pow(0.5 + 0.5 * Math.cos(b.phase), 0.8);
        b.open = 0.1 + 0.9 * (beat * (1 - b.glide) + 0.96 * b.glide);
    }

    @Override
    public boolean startle(B b, double dx, double dy, double strength, Flock<B> flock) {
        double k = strength * 420 * b.depth;
        b.vx += dx * k;
        b.vy += dy * k;
        b.startledUntil = flock.time + AMath.rand(0.6, 1.2);
        b.glideUntil = 0;
        return true;
    }

    private boolean sample(double u, double v, double px, double py) {
        double S = drawS;
        double av = Math.abs(v);
        double bw = bodyHalfWidth(u);
        if (bw > 0 && av * S <= Math.max(bw * S, drawMinBody)) {
            return Ink.ink(ink, BODY, Ink.BODY);
        }
        double vw = av / drawOpen;
        int part;
        if (inLobe(FORE, u - FORE_ROOT, vw)) part = 0;
        else if (inLobe(HIND, u - HIND_ROOT, vw)) part = 1;
        else return false;
        double t = lobeT;
        double r = lobeR;
        // Black margin with white spots on the forewing, dark roots and leading edge, shimmering blue.
        if (r > 0.86) {
            if (part == 0 && r < 0.95 && Math.abs(AMath.frac(t * 5) - 0.5) < 0.14) Ink.ink(ink, 236, 240, 255, Ink.SPOT);
            else Ink.ink(ink, 22, 28, 62, Ink.DARK);
        } else if (r < 0.08) Ink.ink(ink, 26, 44, 110, Ink.DARK);
        else if (part == 0 && t > 0.9) Ink.ink(ink, 30, 50, 120, Ink.DARK);
        else Ink.ink(ink, 50 + 70 * r, 135 + 60 * r, 255, Ink.SHIMMER);
        return true;
    }

    @Override
    public void draw(B b, Pen pen, Flock<B> flock) {
        double S = b.span;
        drawS = S;
        drawOpen = Math.max(0.08, b.open);
        ink = pen.ink;
        // The body yaws a touch with each beat.
        pose.set(b.x, b.y, b.heading + 0.06 * Math.sin(b.phase), S, b.depth, b.id);
        drawMinBody = pen.cw() * 0.4;

        pen.shape(pose, 0.75, sampler, 0.6 + 0.4 * b.open, b.open * 3, Double.NaN);

        // Feelers with clubbed tips.
        double sway = 0.012 * Math.sin(pen.t * 3 + b.id);
        for (int side = -1; side <= 1; side += 2) {
            pen.lineBody(pose, 0.16, 0.012 * side, 0.37 + sway, (0.12 + sway) * side, pen.contrast(),
                    S > 120 ? "o" : null);
        }

        // Downstrokes shed scales from the forewing tips.
        double stroke = Math.max(0, -Math.sin(b.phase)) * (1 - b.glide);
        double phi = FORE.from + (FORE.to - FORE.from) * FORE.peak;
        double tipU = FORE_ROOT + Math.sin(phi) * FORE.reach * 0.95;
        double tipV = Math.cos(phi) * FORE.reach * 0.95 * b.open;
        for (int side = -1; side <= 1; side += 2) {
            pen.toWorld(pose, tipU, tipV * side, at);
            pen.shed(at[0], at[1], (0.02 + 0.1 * stroke) * b.depth);
        }
    }
}

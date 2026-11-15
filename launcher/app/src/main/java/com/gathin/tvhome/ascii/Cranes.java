package com.gathin.tvhome.ascii;

/**
 * Port of cranes.ts: red-crowned cranes, each flying its own way along the course.
 *
 * Like the butterflies, every crane holds the shared course (plus its own small offset), keeps a little room
 * from its neighbours, and always faces where it's headed, so a nudge from a neighbour never turns it round.
 * Each one flaps at its own pace and breaks into glides on its own clock.
 *
 * Look, after the paintings: long neck (white at the base, black toward the head) and pale bill ahead, long
 * legs trailing behind, broad white wings with six splayed fingers at the tips and a ragged black band of
 * secondaries, a white tail fan. The red crown is exactly one cell, drawn last, so it never flickers out as the
 * crane turns; bigger cranes get a glossy black eye either side of it.
 *
 * The flap, by default: the wings open and close evenly, foreshortening toward the body as they rise. With
 * foldFlap on, after how cranes actually fly: a slow, powerful downstroke and a quicker upstroke, with the hand
 * folding at the wrist. Gliding, the wings hold flat with the fingers spread. The body lifts a little on each
 * downstroke.
 */
public final class Cranes extends Animal<Cranes.Crane> {

    public static final class Crane extends Creature {
        public double phase;
        /** This crane's own wingbeat pace, around 1. */
        public double pace;
        /** 0 flapping, 1 gliding. */
        public double glide;
        public double glideUntil;
    }

    /** Where the wings join the body, the wrist, and the tip, as fractions of span from the middle. */
    private static final double ROOT = 0.045;
    private static final double WRIST = 0.25;
    private static final double TIP = 0.5;
    private static final double[] WHITE = {236, 238, 242};
    private static final double[] BLACK = {32, 32, 38};
    /** Black, lifted just enough to read against the night: a true black neck vanishes and the head floats. */
    private static final double[] NECK = {78, 78, 92};
    private static final double[] CROWN = {226, 36, 36};
    private static final double[] CROWN_GLYPH = {255, 214, 200};
    /** Glossy black eyes with a white glint. */
    private static final double[] EYE = {14, 12, 16};
    private static final double[] GLINT = {255, 252, 246};
    private static final double[] BILL = {196, 196, 150};
    private static final double[] LEGS = {60, 60, 66};

    /** Slow downstroke (60% of the beat), quick upstroke with the hand folding back. */
    private static final double DOWN = 0.6;
    private static final double UP_ANGLE = 0.85;
    private static final double DOWN_ANGLE = -0.6;

    private final double[] at = new double[2];
    private final Pose pose = new Pose();

    /** Where a point falls on a flat wing: s from root to tip, q from trailing to leading edge. */
    private double wingS, wingQ;

    /** The wing's pose this frame: arm and hand foreshortening across the body (1 flat). */
    private double strokeArm = 1, strokeHand = 1;
    /** How far the hand sweeps back at the wrist, radians (negative reaches forward). */
    private double strokeSweep;
    /** Body lift (draws a touch bigger), 0 to 1. */
    private double strokeLift;
    /** Precomputed per draw for unfold(): the sweep's cos and sin. */
    private double sweepCos = 1, sweepSin;

    /** unfold() output. */
    private double flatU, flatV;

    /** Per-draw values the sampler reads. */
    private Ink out;
    private double neckW, headR;

    private final Sampler body = (u, v, px, py) -> {
        double av = Math.abs(v);
        // Head, then the long neck: white at the base, black toward the head.
        double hu = u - 0.44;
        if (Math.sqrt(hu * hu + av * av) < headR) return Ink.ink(out, BLACK, Ink.DARK);
        if (u > 0.12 && u < 0.43 && av < neckW) {
            return u > 0.28 ? Ink.ink(out, NECK, Ink.DARK) : Ink.ink(out, WHITE, Ink.SCALE);
        }
        // A white tail fan, widening behind.
        if (Shapes.inEllipse(u, av, -0.19, 0, 0.08, 0.045 + 0.4 * Math.pow(Math.max(0, -0.12 - u), 1.2))) {
            return Ink.ink(out, WHITE, Ink.SCALE);
        }
        if (Shapes.inEllipse(u, av, 0, 0, 0.16, 0.055)) return Ink.ink(out, WHITE, Ink.SCALE);
        // Wings: black secondaries, white elsewhere, a touch paler toward the leading edge.
        if (av <= ROOT) return false;
        unfold(u, av);
        if (!onWing(flatU, flatV)) return false;
        if (wingQ < 0.42 && wingS < 0.7) return Ink.ink(out, BLACK, Ink.DARK);
        double k = 0.9 + 0.1 * wingQ;
        return Ink.ink(out, WHITE[0] * k, WHITE[1] * k, WHITE[2] * k, Ink.SCALE);
    };

    public Cranes() {
        super("cranes", "Crane", 55000, 200, 1.45, 85, new double[] {200, 204, 226});
    }

    @Override
    public void defaults(Tuning t) {
        t.detail = 1.7;
        t.density = 0.8;
        t.speed = 1.5;
        t.sway = 0;
        t.spread = 0;
        t.farthest = 0.35;
        t.farBias = 1.3;
        t.grip = 3;
        t.separation = 150;
        t.trail = 0;
        t.shimmer = 0.1;
        t.sparkle = 0.04;
        t.put("wingbeat", 1);
        t.put("glide", 1);
        t.put("foldFlap", false);
    }

    @Override
    public Crane newCreature() {
        return new Crane();
    }

    @Override
    public void init(Crane c, Flock<Crane> flock) {
        c.phase = AMath.rand(0, AMath.TAU);
        c.pace = 0.8 + 0.4 * AMath.hash3(c.id, 5, 9);
        c.glide = 0;
        c.glideUntil = 0;
    }

    @Override
    public void update(Crane c, Flock<Crane> flock, double dt) {
        Tuning k = flock.tuning;
        double t = flock.time;
        double cruise = flock.cruise(c);
        double course = flock.heading() + c.bias * k.spread;
        flock.begin();
        flock.steer(c, course, cruise, k.grip);
        flock.separate(c, k.separation, 0.45);
        flock.move(c, dt, cruise * 1.6);
        if (flock.passed(c)) flock.reenter(c);
        // Face the course, not the momentary velocity, so a nudge never turns it round.
        flock.face(c, dt, 2, course);

        // Its own rhythm: its own wingbeat pace, and glides on its own clock.
        double glide = k.get("glide");
        if (c.glide < 0.5 && t > c.glideUntil && AMath.random() < 0.25 * glide * dt) {
            c.glideUntil = t + AMath.rand(1, 2.5);
        }
        c.glide += ((t < c.glideUntil ? 1 : 0) - c.glide) * Math.min(1, dt * 3);
        c.phase += dt * AMath.TAU * 1.25 * k.get("wingbeat") * c.pace * (1 - 0.92 * c.glide);
    }

    @Override
    public void draw(Crane c, Pen pen, Flock<Crane> flock) {
        if (flock.tuning.is("foldFlap")) foldingStroke(c);
        else evenStroke(c);
        sweepCos = Math.cos(strokeSweep);
        sweepSin = Math.sin(strokeSweep);
        double S = c.span * (1 + 0.025 * strokeLift);
        out = pen.ink;
        Pose p = pose.set(c.x, c.y, c.heading, S, c.depth, c.id);
        // Never thinner than about a cell, so the neck and head don't flicker.
        neckW = Math.max(0.018, (pen.cw() * 0.45) / S);
        headR = Math.max(0.032, (pen.cw() * 0.6) / S);

        // Long legs trailing, bill ahead.
        for (int side = -1; side <= 1; side += 2) {
            pen.lineBody(p, -0.2, 0.014 * side, -0.62, 0.022 * side, LEGS);
        }
        pen.lineBody(p, 0.47, 0, 0.6, 0, BILL);

        pen.shape(p, 0.66, body, 0.82 + 0.18 * strokeArm, 0, Double.NaN);

        // The red crown: exactly one cell, so it's always there and never flickers.
        pen.toWorld(p, 0.445, 0, at);
        pen.spot(at[0], at[1], "@", CROWN_GLYPH, CROWN, c.depth);

        // Eyes either side of the crown: one glossy black cell each with a white glint, never in the
        // crown's own cell. Only on cranes big enough for that to sit on the head; smaller ones go without.
        if (0.064 * S >= pen.cw() * 1.2) {
            double eyeV = Math.max(0.032, pen.cw() / S);
            for (int side = -1; side <= 1; side += 2) {
                pen.toWorld(p, 0.44, eyeV * side, at);
                pen.spot(at[0], at[1], "●", GLINT, EYE, c.depth);
            }
        }
    }

    /** Where a point falls on a flat wing; sets wingS and wingQ on a hit. */
    private boolean onWing(double u, double vw) {
        if (vw < ROOT || vw > TIP) return false;
        double s = (vw - ROOT) / (TIP - ROOT);
        // Broad, the leading edge sweeping back toward the tip; the chord closes in over the primaries.
        double le = 0.12 + 0.03 * s - 0.09 * s * s;
        double te0 = (-0.075 + 0.03 * s) * 1.2;
        // A ragged trailing edge: the tips of the secondaries.
        if (s < 0.78) te0 += 0.012 * (0.5 + 0.5 * Math.cos(s * AMath.TAU * 9));
        double te = s > 0.72 ? AMath.lerp(te0, le - 0.015, Math.pow((s - 0.72) / 0.28, 1.4)) : te0;
        if (u > le || u < te) return false;
        double q = (u - te) / (le - te);
        // Six splayed primaries at the tip: gaps between the fingers.
        if (s > 0.78 && q < 0.92 && AMath.frac(q * 6) < 0.24) return false;
        wingS = s;
        wingQ = q;
        return true;
    }

    /** The even flap: the whole wing foreshortens toward the body as it rises, from fully open down to a quarter. */
    private void evenStroke(Crane c) {
        double beat = Math.pow(0.5 + 0.5 * Math.cos(c.phase), 0.8);
        double open = Math.max(0.12, 0.25 + 0.75 * (beat * (1 - c.glide) + c.glide));
        strokeArm = open;
        strokeHand = open;
        strokeSweep = 0;
        strokeLift = 0;
    }

    private static double ease(double x) {
        return 0.5 - 0.5 * Math.cos(Math.PI * x);
    }

    /** The folding flap. */
    private void foldingStroke(Crane c) {
        double p = AMath.frac(c.phase / AMath.TAU);
        double elev;
        double sweep;
        double handRaise;
        if (p < DOWN) {
            double x = p / DOWN;
            elev = AMath.lerp(UP_ANGLE, DOWN_ANGLE, ease(x));
            sweep = -0.12 * Math.sin(Math.PI * x);
            handRaise = 0;
            strokeLift = Math.sin(Math.PI * x);
        } else {
            double x = (p - DOWN) / (1 - DOWN);
            elev = AMath.lerp(DOWN_ANGLE, UP_ANGLE, ease(x));
            sweep = 1.05 * Math.sin(Math.PI * x);
            handRaise = 0.45 * Math.sin(Math.PI * x);
            strokeLift = 0;
        }
        // Gliding: wings held flat (a touch raised), fingers spread.
        double g = c.glide;
        elev = AMath.lerp(elev, 0.1, g);
        strokeSweep = AMath.lerp(sweep, 0, g);
        strokeArm = Math.cos(elev);
        // Math.sign(elev || 1) in the original: 1 when elev is 0.
        double sign = elev != 0 ? Math.signum(elev) : 1;
        strokeHand = Math.max(0.2, Math.cos(elev + sign * handRaise * (1 - g)));
        strokeLift *= 1 - g;
    }

    /**
     * Unfold a point seen from above back onto the flat wing: the arm is foreshortened across the body, the hand
     * turned back about the wrist and foreshortened too. Writes flatU, flatV.
     */
    private void unfold(double u, double av) {
        double wristV = ROOT + (WRIST - ROOT) * strokeArm;
        // Turn back about the wrist (on the leading edge's line, near u = 0.03), then unfold the hand.
        double pu = 0.03;
        double du = u - pu;
        double dv = av - wristV;
        double cs = sweepCos;
        double sn = sweepSin;
        double dv0 = -du * sn + dv * cs;
        if (av <= wristV || dv0 < 0) {
            // The arm (or just inside the wrist after turning).
            flatU = u;
            flatV = ROOT + (av - ROOT) / strokeArm;
        } else {
            flatU = pu + du * cs + dv * sn;
            flatV = WRIST + dv0 / strokeHand;
        }
    }
}

package com.gathin.tvhome.ascii;

/**
 * Port of ascii-animals src/animals/frogs.ts (MIT, drewdi). Leopard frogs, leaping along the course.
 *
 * A frog sits (breathing, blinking, slowly turning to face its next jump), then leaps: it flies in a straight
 * line over the ground while its height follows a parabola. Seen from above, height means coming toward you, so
 * the frog draws bigger at the top of the arc. The hind legs unfold from a folded Z into a long straight kick and
 * fold back on landing, the front legs reach forward to catch it, and each landing splashes into the trail.
 *
 * Look, from above: a pear-shaped body widest at the hips and a broad blunt head; pale ridges down the back, dark
 * spots with pale halos, banded legs, splayed toes and webbed hind feet. Each eye is exactly one cell (white with
 * a solid black pupil, or a closed lid mid-blink), so the pair always matches however the frog turns.
 */
public final class Frogs extends Animal<Frogs.F> {
    public static final class F extends Creature {
        public boolean leaping;
        /** When the current sit ends. */
        public double until;
        /** Where the next (or current) leap is headed. */
        public double aim;
        /** Progress through the current leap, 0 to 1. */
        public double s;
        public double dur = 0.5;
        public double fromX, fromY, toX, toY;
    }

    private static final double[] SKIN = {104, 184, 76};
    private static final double[] LEG = {92, 164, 66};
    private static final double[] SPOT = {30, 70, 30};
    private static final double[] HALO = {190, 222, 130};
    private static final double[] RIDGE = {206, 226, 150};
    private static final double[] BAND = {36, 80, 32};
    /** Big white eyes with solid black pupils. */
    private static final double[] EYE_WHITE = {240, 240, 228};
    private static final double[] PUPIL = {16, 16, 12};
    private static final double[] LID = {20, 30, 16};
    private static final double[] WEB = {120, 190, 90};

    /** Hind leg (hip, knee, ankle, foot) for the right side: folded in a Z at rest, kicked straight back mid-leap. */
    private static final double[][] HIND_REST = {{-0.16, 0.11}, {0.0, 0.22}, {-0.19, 0.25}, {-0.04, 0.31}};
    private static final double[][] HIND_KICK = {{-0.17, 0.09}, {-0.38, 0.15}, {-0.56, 0.13}, {-0.68, 0.15}};
    private static final double[] HIND_RADII = {0.056, 0.04, 0.026};
    /** Front leg (shoulder, elbow, hand): reaching forward at rest, tucked back mid-leap, reaching to land. */
    private static final double[][] FRONT_REST = {{0.06, 0.1}, {0.09, 0.19}, {0.18, 0.2}};
    private static final double[][] FRONT_AIR = {{0.06, 0.1}, {0.02, 0.16}, {-0.06, 0.15}};
    private static final double[][] FRONT_REACH = {{0.08, 0.11}, {0.16, 0.19}, {0.27, 0.19}};
    private static final double[] FRONT_RADII = {0.028, 0.022};
    /** Where the eyes sit (right side), in body coordinates. */
    private static final double[] EYE = {0.18, 0.1};

    /** How far (radians) a leap may stray from the course, whatever the Spread knob says. */
    private static final double STRAY = 0.35;
    /** Leap lengths to try (x the natural one), so a crowded frog lands clear without turning. */
    private static final double[] LENGTHS = {1, 0.8, 1.2, 0.65, 1.35};

    private static final String EYE_GLYPH = "●";

    // Per-draw scratch, reused every frame (draws run one creature at a time on the main thread).
    private final double[] at = new double[2];
    private final Pose pose = new Pose();
    private final double[][] hindBuf = new double[4][2];
    private final double[][] frontBuf = new double[3][2];
    private double[][] dHind = HIND_REST;
    private double[][] dFront = FRONT_REST;
    private double dBreathe = 1;
    private int dId;
    private double dWebDir;
    private Ink dOut;

    private final Sampler body = (u, v, px, py) -> {
        Ink out = dOut;
        double av = Math.abs(v);
        // Body: pale ridges down the back, dark spots with pale halos, a soft highlight down the middle.
        double hw = halfWidth(u, dBreathe);
        if (av <= hw && u > -0.29 && u < 0.3) {
            if (Math.abs(av - 0.11) < 0.012 && u > -0.22 && u < 0.14) return Ink.ink(out, RIDGE, Ink.LIT);
            if (u < 0.1) {
                if (Shapes.spotted(u, v, 0.085, 0.024, dId)) return Ink.ink(out, SPOT, Ink.DARK);
                if (Shapes.spotted(u, v, 0.085, 0.036, dId)) return Ink.ink(out, HALO, Ink.LIT);
            }
            double g = 0.9 + 0.2 * (1 - av / Math.max(hw, 1e-3));
            return Ink.ink(out, SKIN[0] * g, SKIN[1] * g, SKIN[2] * g, Ink.LIT);
        }
        // Webbing between the hind toes.
        double fu = dHind[3][0];
        double fv = dHind[3][1];
        double off = Math.atan2(av - fv, u - fu) - dWebDir;
        if (Math.hypot(u - fu, av - fv) < 0.06 && Math.abs(Math.atan2(Math.sin(off), Math.cos(off))) < 0.5) {
            return Ink.ink(out, WEB, Ink.GLASS, ".");
        }
        // Banded legs.
        if (onLimb(dHind, HIND_RADII, u, av) || onLimb(dFront, FRONT_RADII, u, av)) {
            return AMath.frac(Shapes.segT * 4.5) < 0.3 ? Ink.ink(out, BAND, Ink.DARK) : Ink.ink(out, LEG, Ink.LIT);
        }
        return false;
    };

    public Frogs() {
        super("frogs", "Frog", 75000, 200, 0.95, 80, new double[] {110, 200, 220});
    }

    @Override
    public void defaults(Tuning t) {
        t.fog = 0;
        t.size = 1.2;
        t.density = 1.3;
        t.sway = 0;
        t.spread = 0;
        t.farthest = 0.35;
        t.farBias = 1.4;
        t.separation = 450;
        t.trail = 3.45;
        t.trailFade = 0.4;
        t.shimmer = 0.15;
        t.sparkle = 0.05;
        t.put("hop", 1);
        t.put("leap", 0.45);
        t.put("height", 1);
    }

    @Override
    public F newCreature() {
        return new F();
    }

    @Override
    public void init(F c, Flock<F> flock) {
        c.leaping = false;
        c.until = 0;
        c.aim = 0;
        c.s = 0;
        c.dur = 0.5;
        c.fromX = 0;
        c.fromY = 0;
        c.toX = 0;
        c.toY = 0;
    }

    @Override
    public void enter(F f, Flock<F> flock) {
        f.leaping = false;
        f.s = 0;
        f.vx = 0;
        f.vy = 0;
        f.aim = flock.heading() + f.bias * flock.tuning.spread;
        f.heading = f.aim;
        f.until = flock.time + AMath.rand(0, 1.5);
    }

    @Override
    public void update(F f, Flock<F> flock, double dt) {
        Tuning k = flock.tuning;
        double t = flock.time;
        if (!f.leaping) {
            f.vx = 0;
            f.vy = 0;
            flock.face(f, dt, 2.5, f.aim);
            if (t >= f.until) leap(f, flock, f.aim, 1, true);
            return;
        }
        f.s += dt / f.dur;
        if (f.s >= 1) {
            f.leaping = false;
            f.x = f.toX;
            f.y = f.toY;
            f.vx = 0;
            f.vy = 0;
            f.until = t + AMath.rand(0.5, 2.2) / (k.get("hop") * k.speed);
            f.aim = flock.heading() + (f.bias + AMath.rand(-0.7, 0.7)) * k.spread;
            flock.events.add(new Flock.WorldEvent(f.x, f.y, 2, 0.7 * f.depth * k.trail));
            if (flock.passed(f)) flock.reenter(f);
            return;
        }
        f.x = AMath.lerp(f.fromX, f.toX, f.s);
        f.y = AMath.lerp(f.fromY, f.toY, f.s);
    }

    /** A startle scares nearby frogs into leaping away from it, whichever way that is. */
    @Override
    public boolean startle(F f, double dx, double dy, double strength, Flock<F> flock) {
        if (f.leaping) return true;
        leap(f, flock, Math.atan2(dy, dx), 1 + strength, false);
        f.startledUntil = flock.time + f.dur;
        return true;
    }

    /**
     * Launch a leap toward dir. On course, the direction never bends (beyond the Spread knob, capped at STRAY);
     * instead, to keep from landing on another frog, it picks whichever leap length along that line lands
     * clearest. Scared off by a startle (onCourse false), it just leaps straight away.
     */
    private static void leap(F f, Flock<F> flock, double dir, double boost, boolean onCourse) {
        Tuning k = flock.tuning;
        double kLeap = k.get("leap");
        if (onCourse) {
            double heading = flock.heading();
            dir = heading + AMath.clamp(AMath.angleDiff(heading, dir), -STRAY, STRAY);
        }
        double cos = Math.cos(dir);
        double sin = Math.sin(dir);
        double reach = f.span * AMath.rand(2.4, 3.6) * kLeap * k.speed * boost;
        double dist = reach;
        if (onCourse && k.separation > 0) {
            // Nearest to the natural length first; take the first that lands clear, else the clearest.
            double best = -1;
            for (double m : LENGTHS) {
                double x = f.x + cos * reach * m;
                double y = f.y + sin * reach * m;
                double clear = Double.POSITIVE_INFINITY;
                for (int i = 0, n = flock.creatures.size(); i < n; i++) {
                    F o = flock.creatures.get(i);
                    if (o == f || Math.abs(o.depth - f.depth) > 0.2) continue;
                    double ox = o.leaping ? o.toX : o.x;
                    double oy = o.leaping ? o.toY : o.y;
                    clear = Math.min(clear, Math.hypot(x - ox, y - oy) / (f.span + o.span));
                }
                if (clear > best) {
                    best = clear;
                    dist = reach * m;
                }
                if (clear >= 0.6 * (k.separation / 300)) break;
            }
        }
        f.leaping = true;
        f.s = 0;
        f.dur = AMath.rand(0.42, 0.55) * Math.sqrt(kLeap) * (boost > 1 ? 0.8 : 1);
        f.heading = dir;
        f.fromX = f.x;
        f.fromY = f.y;
        f.toX = f.x + cos * dist;
        f.toY = f.y + sin * dist;
        f.vx = (f.toX - f.fromX) / f.dur;
        f.vy = (f.toY - f.fromY) / f.dur;
    }

    @Override
    public void draw(F f, Pen pen, Flock<F> flock) {
        double height = flock.tuning.get("height");
        double s = f.leaping ? f.s : 0;
        double h = 4 * s * (1 - s) * height;
        double S = f.span * (1 + 0.35 * h);
        Pose pose = this.pose.set(f.x, f.y, f.heading, S, f.depth, f.id);

        // Legs: folded at rest, kicked straight back through the middle of a leap, tucking again to land.
        double kick = f.leaping ? AMath.smoothstep(0, 0.16, s) * (1 - AMath.smoothstep(0.72, 1, s)) : 0;
        double[][] hind = blend(HIND_REST, HIND_KICK, kick, hindBuf);
        double[][] front = f.leaping
                ? s < 0.6 ? blend(FRONT_REST, FRONT_AIR, AMath.smoothstep(0, 0.2, s), frontBuf)
                        : blend(FRONT_AIR, FRONT_REACH, AMath.smoothstep(0.6, 0.95, s), frontBuf)
                : FRONT_REST;
        // Breathing: the throat swells while sitting. A blink now and then.
        double breathe = f.leaping ? 1 : 1 + 0.045 * Math.sin(pen.t * 6 + f.id);
        boolean blinking = !f.leaping && AMath.frac(pen.t * 0.21 + f.id * 0.37) < 0.035;

        // Toes: splayed lines off each hand and foot (trailing behind mid-leap).
        for (int side = -1; side <= 1; side += 2) {
            toes(pen, pose, side, hind[2], hind[3], 4, 0.075, 1.2);
            toes(pen, pose, side, front[1], front[2], 3, 0.045, 1.1);
        }

        dOut = pen.ink;
        dHind = hind;
        dFront = front;
        dBreathe = breathe;
        dId = f.id;
        dWebDir = Math.atan2(hind[3][1] - hind[2][1], hind[3][0] - hind[2][0]);
        pen.shape(pose, 0.9, body, 1 + 0.15 * h, 0, Double.NaN);

        // Eyes: exactly one cell each, so the pair always matches however the frog turns.
        for (int side = -1; side <= 1; side += 2) {
            pen.toWorld(pose, EYE[0], EYE[1] * side, at);
            if (blinking) pen.spot(at[0], at[1], "-", LID, SKIN, f.depth);
            else pen.spot(at[0], at[1], EYE_GLYPH, PUPIL, EYE_WHITE, f.depth);
        }
    }

    private static void toes(Pen pen, Pose pose, int side, double[] from, double[] to, int n, double len, double fan) {
        double dir = Math.atan2(to[1] - from[1], to[0] - from[0]);
        for (int i = 0; i < n; i++) {
            double a = dir + ((double) i / (n - 1) - 0.5) * fan;
            pen.lineBody(pose, to[0], to[1] * side, to[0] + Math.cos(a) * len, (to[1] + Math.sin(a) * len) * side, WEB);
        }
    }

    private static double[][] blend(double[][] a, double[][] b, double t, double[][] out) {
        for (int i = 0; i < a.length; i++) {
            out[i][0] = AMath.lerp(a[i][0], b[i][0], t);
            out[i][1] = AMath.lerp(a[i][1], b[i][1], t);
        }
        return out;
    }

    /** Half-width of the body at u: a pear widest at the hips, then a broad, blunt head. */
    private static double halfWidth(double u, double breathe) {
        double tu = (u + 0.1) / 0.18;
        double trunk = 0.155 * Math.sqrt(Math.max(0, 1 - tu * tu));
        double hu = (u - 0.12) / 0.18;
        double head = 0.15 * breathe * Math.sqrt(Math.max(0, 1 - hu * hu));
        if (u > 0.18) {
            double q = (u - 0.18) / 0.12;
            head *= 1 - 0.5 * q * q;
        }
        return Math.max(trunk, head);
    }

    /** On a limb chain (tapered capsules)? Writes how far down it into Shapes.segT. */
    private static boolean onLimb(double[][] joints, double[] radii, double u, double v) {
        for (int i = 0; i < joints.length - 1; i++) {
            double[] a = joints[i];
            double[] b = joints[i + 1];
            if (Shapes.toSegment(u, v, a[0], a[1], b[0], b[1]) <= radii[i] * (1 - 0.25 * Shapes.segT)) {
                Shapes.segT = (i + Shapes.segT) / (joints.length - 1);
                return true;
            }
        }
        return false;
    }
}

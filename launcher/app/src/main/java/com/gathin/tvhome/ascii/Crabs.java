package com.gathin.tvhome.ascii;

/**
 * Port of crabs.ts: shore crabs, scuttling sideways along the course.
 *
 * A crab faces across the course and walks sideways, in bursts: scuttle, stop, scuttle. Its four pairs of legs
 * step in alternating sets, drawn as jointed line glyphs, and leave footprints in the trail that linger like
 * tracks in sand. A startle scares crabs nearby into bolting sideways away from it, fast, with claws up and
 * open; the nearer, the faster and longer they run before calming down and rejoining the course.
 * The carapace is a wide rounded shell with a toothed front edge, a pale centre and darker flecks, and two
 * glossy black eyes on stalks at the front.
 */
public final class Crabs extends Animal<Crabs.Crab> {

    public static final class Crab extends Creature {
        public boolean running;
        /** When the current burst or pause ends. */
        public double until;
        /** Which way it faces relative to where it walks: +1 walking to its right, -1 to its left. */
        public int facing;
        /** Leg cycle phase. */
        public double gait;
        /** 0 claws resting, 1 raised and open. */
        public double alarm;
        /** Bolting from a startle: which way, how fast at the start, and when it began. */
        public double fleeDir;
        public double fleeSpeed;
        public double fleeFrom;
    }

    /** Glossy black eyes with a white glint. */
    private static final double[] EYE = {14, 10, 10};
    private static final double[] GLINT = {255, 252, 246};

    private static final double[] SHELL = {204, 82, 52};
    private static final double[] PALE = {232, 132, 92};
    private static final double[] FLECK = {140, 46, 32};
    private static final double[] CLAW = {214, 92, 56};
    private static final double[] TIP = {60, 30, 30};
    private static final double[] LEG = {186, 72, 44};

    /** Leg roots along each side of the shell (u), front to back. */
    private static final double[] HIPS = {0.07, -0.01, -0.08, -0.14};

    /** The pincer frame's angle. */
    private static final double ANG = 0.55;
    private static final double ANG_COS = Math.cos(ANG);
    private static final double ANG_SIN = Math.sin(ANG);

    private final double[] at = new double[2];
    private final Pose pose = new Pose();

    /** Per-draw values the sampler reads. */
    private Ink out;
    private double raise;
    private int id;

    private final Sampler body = (u, v, px, py) -> {
        double av = Math.abs(v);
        // Shell: a wide superellipse with a toothed front edge.
        double su = Math.abs(u / 0.17);
        double sv = av / 0.27;
        double teeth = u > 0.06 ? 0.06 * (0.5 + 0.5 * Math.cos(sv * Math.PI * 7)) : 0;
        if (Math.pow(su, 2.2) + Math.pow(sv, 2.4) <= 1 - teeth) {
            if (Shapes.spotted(u, v, 0.05, 0.012, id)) return Ink.ink(out, FLECK, Ink.DARK);
            // An H-shaped groove across the back.
            if (Math.abs(av - 0.08) < 0.01 && u > -0.08 && u < 0.05) return Ink.ink(out, FLECK, Ink.DARK, "|");
            return Shapes.inEllipse(u, av, 0.01, 0, 0.1, 0.13) ? Ink.ink(out, PALE, Ink.SCALE)
                    : Ink.ink(out, SHELL, Ink.SCALE);
        }
        // Claws: an arm out to each side, then a pincer pointing forward and in.
        double armU = 0.26 + 0.07 * raise;
        double armV = 0.25 + 0.05 * raise;
        if (Shapes.toSegment(u, av, 0.12, 0.17, armU, armV) < 0.034) return Ink.ink(out, CLAW, Ink.SCALE);
        double cu = 0.34 + 0.08 * raise;
        double cv = 0.19 + 0.04 * raise;
        double size = 1 + 0.15 * raise;
        // Pincer frame: x along the claw toward its tip, y across it.
        double lx = ((u - cu) * ANG_COS - (av - cv) * ANG_SIN) / size;
        double ly = ((u - cu) * ANG_SIN + (av - cv) * ANG_COS) / size;
        double ex = lx / 0.09;
        double ey = ly / 0.055;
        if (ex * ex + ey * ey <= 1) {
            // The open jaw between the two fingers.
            double gap = 0.15 + 0.55 * raise;
            if (lx > 0.02 && Math.abs(ly) < gap * (lx - 0.02)) return false;
            return lx > 0.06 ? Ink.ink(out, TIP, Ink.DARK) : Ink.ink(out, CLAW, Ink.SCALE);
        }
        return false;
    };

    public Crabs() {
        super("crabs", "Crab", 70000, 200, 0.8, 95, new double[] {206, 178, 136});
    }

    @Override
    public void defaults(Tuning t) {
        t.detail = 1.7;
        t.fog = 0.12;
        t.density = 1.8;
        t.sway = 0;
        t.spread = 0;
        t.farthest = 0.25;
        t.farBias = 0.7;
        t.grip = 3;
        t.separation = 400;
        t.trail = 1.2;
        t.trailFade = 0.35;
        t.shimmer = 0.1;
        t.sparkle = 0.03;
        t.put("burst", 1);
        t.put("rest", 1);
        t.put("legSpeed", 1);
        t.put("scare", 0.55);
    }

    @Override
    public Crab newCreature() {
        return new Crab();
    }

    @Override
    public void init(Crab c, Flock<Crab> flock) {
        c.running = true;
        c.until = 0;
        c.facing = AMath.random() < 0.5 ? 1 : -1;
        c.gait = AMath.rand(0, AMath.TAU);
        c.alarm = 0;
        c.fleeDir = 0;
        c.fleeSpeed = 0;
        c.fleeFrom = 0;
    }

    @Override
    public void enter(Crab c, Flock<Crab> flock) {
        c.running = true;
        c.until = flock.time + AMath.rand(0.3, 1.2);
        c.heading = flock.course() - (c.facing * Math.PI) / 2;
    }

    @Override
    public void update(Crab c, Flock<Crab> flock, double dt) {
        Tuning k = flock.tuning;
        double t = flock.time;
        double rest = k.get("rest");
        if (t >= c.until) {
            c.running = !c.running || rest <= 0;
            c.until = t + (c.running ? AMath.rand(0.5, 1.6) * k.get("burst") : AMath.rand(0.3, 1.4) * rest);
        }
        double cruise = flock.cruise(c);
        double course = flock.heading() + c.bias * k.spread;
        boolean fleeing = t < c.startledUntil;
        // Bolting: run away from the startle, easing from full speed back down as it calms.
        double calm = fleeing ? (t - c.fleeFrom) / (c.startledUntil - c.fleeFrom) : 1;
        double walk = fleeing ? c.fleeDir : course;
        flock.begin();
        if (fleeing) flock.steer(c, c.fleeDir, Math.max(cruise, c.fleeSpeed * (1 - calm * calm)), 8);
        else flock.steer(c, course, c.running ? cruise : 0, k.grip * 2);
        flock.separate(c);
        double speed = flock.move(c, dt, fleeing ? Math.max(cruise * 2, c.fleeSpeed) : cruise * 2);
        if (flock.passed(c)) flock.reenter(c);

        // Sideways: the body faces across the way it walks (it scuttles off sideways when bolting too).
        // Bolting, whichever side is nearer the way it runs leads, so it doesn't spin round first.
        int side = fleeing && Math.abs(AMath.angleDiff(c.heading + (c.facing * Math.PI) / 2, walk)) > Math.PI / 2
                ? -c.facing : c.facing;
        double want = walk - (side * Math.PI) / 2;
        double turn = (fleeing ? 10 : 3) * dt;
        c.heading += Math.max(-turn, Math.min(turn, AMath.angleDiff(c.heading, want)));
        c.gait += (dt * speed * k.get("legSpeed")) / Math.max(4, c.span * 0.09);
        c.alarm = Math.max(0, c.alarm - dt * 0.7);
    }

    @Override
    public boolean startle(Crab c, double dx, double dy, double strength, Flock<Crab> flock) {
        double scare = flock.tuning.get("scare");
        // Handled here either way: with scare off the crab ignores it (no default push).
        if (scare <= 0) return true;
        // Away from the startle, a little scattered so a cluster fans out.
        c.fleeDir = Math.atan2(dy, dx) + AMath.rand(-0.35, 0.35);
        c.fleeSpeed = flock.cruise(c) * (2.5 + 3 * strength) * scare;
        c.fleeFrom = flock.time;
        c.startledUntil = flock.time + (1 + 1.5 * strength) * Math.sqrt(scare);
        c.vx = Math.cos(c.fleeDir) * c.fleeSpeed;
        c.vy = Math.sin(c.fleeDir) * c.fleeSpeed;
        c.running = true;
        c.until = c.startledUntil + AMath.rand(0.3, 1);
        c.alarm = 1;
        return true;
    }

    @Override
    public void draw(Crab c, Pen pen, Flock<Crab> flock) {
        double S = c.span;
        out = pen.ink;
        Pose p = pose.set(c.x, c.y, c.heading, S, c.depth, c.id);

        // Legs first, so the shell covers their roots. Alternate legs step together.
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < HIPS.length; i++) {
                double hu = HIPS[i];
                double ph = c.gait + i * Math.PI * 0.5 + (side > 0 ? Math.PI : 0);
                double cph = Math.cos(ph);
                double lift = Math.max(0, Math.sin(ph));
                double reach = 0.04 * cph;
                double splay = (i - 1.5) * 0.06;
                double kneeU = hu + splay * 0.6;
                double kneeV = (0.36 - 0.03 * lift) * side;
                double tipU = hu + splay * 1.4 + reach;
                double tipV = (0.52 + 0.03 * cph - 0.06 * lift) * side;
                pen.lineBody(p, hu, 0.22 * side, kneeU, kneeV, LEG);
                pen.lineBody(p, kneeU, kneeV, tipU, tipV, LEG);
                // A footprint each time a foot comes down.
                if (lift < 0.05 && cph > 0.9) {
                    pen.toWorld(p, tipU, tipV, at);
                    pen.shed(at[0], at[1], 0.12 * c.depth);
                }
            }
        }

        raise = c.alarm;
        id = c.id;
        pen.shape(p, 0.62, body);

        // Eyes on stalks at the front: one glossy black cell each with a white glint, so they always
        // show and always match. Kept about a cell apart so small crabs still get two.
        double eyeV = Math.min(0.14, Math.max(0.06, (pen.cw() * 0.55) / S));
        for (int side = -1; side <= 1; side += 2) {
            pen.toWorld(p, 0.2, eyeV * side, at);
            pen.spot(at[0], at[1], "●", GLINT, EYE, c.depth);
        }
    }
}

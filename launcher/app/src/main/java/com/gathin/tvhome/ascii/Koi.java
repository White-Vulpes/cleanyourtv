package com.gathin.tvhome.ascii;

/**
 * Port of ascii-animals src/animals/koi.ts (MIT, drewdi). Koi, meandering lazily along the course.
 *
 * <p>Each koi is a follow-the-leader spine: the head swims, every node behind it trails at a fixed spacing, and
 * a stiffness limit per joint keeps the body from folding. On top of that a travelling wave, growing toward the
 * tail, swishes the body as it swims. The silhouette is sampled against that bent spine (distance to it gives
 * how far across the body a point is; arc length gives how far along), so patterns ride along with the bends.
 * Each fish is one of a few classic varieties, painted with a smooth noise field. They leave soft ripples.
 *
 * <p>The original's tap drops food the koi swim over to eat. The TV has no pointer, so tap and the food it
 * creates (pellets, their drift, the hunger steering and the gulp) are left out: with no food the koi always
 * take the meandering branch, exactly as the original does when the pond is empty.
 */
public final class Koi extends Animal<Koi.K> {
    private static final int NODES = 14;

    public static final class K extends Creature {
        /** Spine, head first: [x0, y0, x1, y1, ...]. */
        public final double[] nodes = new double[NODES * 2];
        /** Tail-beat phase. */
        public double phase;
        /** Which pattern: see the VARIETY constants. */
        public int variety;
    }

    private static final double[] WHITE = {244, 240, 230};
    private static final double[] RED = {222, 66, 40};
    private static final double[] BLACK = {34, 34, 40};
    private static final double[] GOLD = {238, 182, 64};
    private static final double[] ORANGE = {236, 124, 44};
    private static final double[] FIN = {236, 226, 214};
    /** Glossy black eyes with a white glint. */
    private static final double[] EYE = {14, 12, 16};
    private static final double[] GLINT = {255, 252, 246};

    /** kohaku (red on white), sanke (kohaku + black flecks), showa (black base, red and white), ogon (gold), chagoi (orange). */
    private static final int KOHAKU = 0, SANKE = 1, SHOWA = 2, OGON = 3;
    private static final int VARIETIES = 5;

    // Per-draw scratch: the rendered spine and its arc length, plus what the sampler reads for the koi being drawn.
    private final double[] spine = new double[NODES * 2];
    private final double[] arc = new double[NODES];
    private final Pose pose = new Pose();
    private double curL, curTotal, curSeed, curFinBeat;
    private int curVariety, curId;
    private Pen curPen;
    private final Sampler body = this::sampleBody;

    public Koi() {
        super("koi", "Koi", 75000, 200, 1.15, 50, new double[] {110, 170, 220});
    }

    @Override
    public void defaults(Tuning t) {
        t.fog = 0.64;
        t.density = 1.6;
        t.speed = 1.6;
        t.spread = 0;
        t.sway = 0;
        t.farthest = 0.33;
        t.farBias = 1.3;
        t.grip = 2.65;
        t.separation = 780;
        t.trail = 4;
        t.trailFade = 0.4;
        t.shimmer = 0.35;
        t.sparkle = 0.08;
        t.put("meander", 0.4);
        t.put("tailBeat", 1);
    }

    @Override
    public K newCreature() {
        return new K();
    }

    @Override
    public void init(K c, Flock<K> flock) {
        c.phase = AMath.rand(0, AMath.TAU);
        c.variety = (int) Math.floor(AMath.hash3(c.id, 7, 3) * VARIETIES);
    }

    /** Body half-width at s (0 snout to 1 tail tip), as a fraction of length. */
    private static double halfWidth(double s) {
        if (s < 0.12) return 0.1 * Math.pow(Math.sin(((s / 0.12) * Math.PI) / 2), 0.6);
        if (s < 0.35) return 0.1 + 0.016 * Math.sin(((s - 0.12) / 0.23) * (Math.PI / 2));
        if (s < 0.82) return 0.116 - 0.088 * AMath.smoothstep(0.35, 0.82, s);
        // The caudal fin fans out, forked at the end.
        return 0.028 + ((s - 0.82) / 0.18) * 0.1;
    }

    /** A smooth blotchy field, different per fish. */
    private static double blotch(double s, double a, double seed) {
        return Math.sin(s * 11 + seed) * Math.cos(a * 22 + seed * 1.3)
                + 0.6 * Math.sin(s * 23 + a * 15 + seed * 2.1)
                + 0.35 * Math.sin(s * 5 - seed * 0.7);
    }

    /** Lay the spine out straight behind the head. */
    @Override
    public void enter(K f, Flock<K> flock) {
        double seg = f.span / (NODES - 1);
        for (int k = 0; k < NODES; k++) {
            f.nodes[k * 2] = f.x - Math.cos(f.heading) * seg * k;
            f.nodes[k * 2 + 1] = f.y - Math.sin(f.heading) * seg * k;
        }
    }

    @Override
    public void update(K f, Flock<K> flock, double dt) {
        Tuning k = flock.tuning;
        double meander = k.get("meander");
        double tailBeat = k.get("tailBeat");
        double t = flock.time;
        // No food on the TV (hunger 0): lazy S-curves around the course, and a slow turn rate.
        double want = flock.heading() + f.bias * k.spread + meander * 0.6 * Math.sin(t * 0.35 + f.id * 1.7);
        double[] acc = flock.begin();
        flock.separate(f, k.separation, 0.5);
        if (acc[0] != 0 || acc[1] != 0) {
            double cruise = flock.cruise(f);
            want = Math.atan2(Math.sin(want) * cruise + acc[1] * 0.3, Math.cos(want) * cruise + acc[0] * 0.3);
        }
        double turn = 0.9 * k.grip * dt;
        f.heading += AMath.clamp(AMath.angleDiff(f.heading, want), -turn, turn);
        double speed = flock.cruise(f) * (0.85 + 0.25 * Math.sin(t * 0.5 + f.id));
        f.vx = Math.cos(f.heading) * speed;
        f.vy = Math.sin(f.heading) * speed;
        f.x += f.vx * dt;
        f.y += f.vy * dt;
        f.phase += dt * (2 + speed / 25) * tailBeat;

        // Follow-the-leader spine with a stiffness limit per joint.
        double[] n = f.nodes;
        double seg = f.span / (NODES - 1);
        n[0] = f.x;
        n[1] = f.y;
        double prev = f.heading + Math.PI;
        for (int i = 1; i < NODES; i++) {
            double a = Math.atan2(n[i * 2 + 1] - n[i * 2 - 1], n[i * 2] - n[i * 2 - 2]);
            a = prev + AMath.clamp(AMath.angleDiff(prev, a), -0.22, 0.22);
            n[i * 2] = n[i * 2 - 2] + Math.cos(a) * seg;
            n[i * 2 + 1] = n[i * 2 - 1] + Math.sin(a) * seg;
            prev = a;
        }
        if (flock.passed(f, f.span * 1.3)) flock.reenter(f);
    }

    /** Draw one koi: body and pattern along the bent spine, see-through fins, a forked tail, glossy eyes on the bigger ones. */
    @Override
    public void draw(K f, Pen pen, Flock<K> flock) {
        double tailBeat = flock.tuning.get("tailBeat");
        double L = f.span;
        double[] n = f.nodes;

        // The rendered spine: the sim spine plus a travelling wave that grows toward the tail.
        double[] spine = this.spine;
        double[] arc = this.arc;
        arc[0] = 0;
        for (int i = 0; i < NODES; i++) {
            double s = (double) i / (NODES - 1);
            int a = Math.max(0, i - 1);
            int b = Math.min(NODES - 1, i + 1);
            double tx = n[a * 2] - n[b * 2];
            double ty = n[a * 2 + 1] - n[b * 2 + 1];
            double tl = Math.hypot(tx, ty);
            if (tl == 0) tl = 1;
            tx /= tl;
            ty /= tl;
            double off = L * 0.06 * Math.pow(s, 1.6) * Math.sin(f.phase - s * 4.5) * Math.min(1.5, tailBeat);
            spine[i * 2] = n[i * 2] - ty * off;
            spine[i * 2 + 1] = n[i * 2 + 1] + tx * off;
            if (i > 0) arc[i] = arc[i - 1] + Math.hypot(spine[i * 2] - spine[i * 2 - 2], spine[i * 2 + 1] - spine[i * 2 - 1]);
        }
        double total = arc[NODES - 1];
        if (total == 0) total = 1;
        int mid = NODES / 2;
        pose.set(spine[mid * 2], spine[mid * 2 + 1], f.heading, L, f.depth, f.id);

        curPen = pen;
        curL = L;
        curTotal = total;
        curSeed = f.id * 1.37;
        curFinBeat = 0.8 + 0.2 * Math.sin(f.phase * 0.7);
        curVariety = f.variety;
        curId = f.id;
        pen.shape(pose, 0.72, body);
        curPen = null;

        // Eyes: one glossy black cell each with a white glint, on the head just inside the outline, so they always
        // show and always match. Only on koi big enough for the two to sit in cells of their own; smaller ones go
        // without.
        double across = halfWidth(0.09) * 0.55 * L;
        if (2 * across >= pen.cw()) {
            double along = 0.09 * total;
            int i = 0;
            while (i < NODES - 2 && arc[i + 1] < along) i++;
            double sl = arc[i + 1] - arc[i];
            if (sl == 0) sl = 1;
            double q = (along - arc[i]) / sl;
            double dx = (spine[i * 2 + 2] - spine[i * 2]) / sl;
            double dy = (spine[i * 2 + 3] - spine[i * 2 + 1]) / sl;
            double hx = spine[i * 2] + dx * q * sl;
            double hy = spine[i * 2 + 1] + dy * q * sl;
            for (int side = -1; side <= 1; side += 2) {
                pen.spot(hx - dy * across * side, hy + dx * across * side, "●", GLINT, EYE, f.depth);
            }
        }

        // Soft ripples off the tail.
        pen.shed(spine[(NODES - 2) * 2], spine[(NODES - 2) * 2 + 1], 0.02 * f.depth);
    }

    /** The koi sampler, against the spine and fields set by draw(). */
    private boolean sampleBody(double u, double v, double px, double py) {
        double[] spine = this.spine;
        double[] arc = this.arc;
        double L = curL;
        Ink out = curPen.ink;
        // Nearest point on the spine: how far along (s) and across (a, signed) the body.
        double best = Double.POSITIVE_INFINITY;
        double s = 0;
        double a = 0;
        // How far past the tail tip the point lies (fractions of length), if it does.
        double past = 0;
        for (int i = 0; i < NODES - 1; i++) {
            double ax = spine[i * 2];
            double ay = spine[i * 2 + 1];
            double sx = spine[i * 2 + 2] - ax;
            double sy = spine[i * 2 + 3] - ay;
            double len2 = sx * sx + sy * sy;
            if (len2 == 0) len2 = 1;
            double raw = ((px - ax) * sx + (py - ay) * sy) / len2;
            double q = AMath.clamp(raw, 0, 1);
            double sl = Math.sqrt(len2);
            double cross = sx * (py - ay) - sy * (px - ax);
            if (i == NODES - 2 && raw > 1) {
                // Beyond the tip: measure across from the tip's own line, not as distance to the tip point.
                double acrossTip = Math.abs(cross) / sl;
                if (acrossTip * acrossTip < best) {
                    best = acrossTip * acrossTip;
                    s = 1;
                    past = ((raw - 1) * sl) / L;
                    a = (acrossTip / L) * (cross >= 0 ? 1 : -1);
                }
                continue;
            }
            double ex = px - ax - sx * q;
            double ey = py - ay - sy * q;
            double d2 = ex * ex + ey * ey;
            if (d2 < best) {
                best = d2;
                s = (arc[i] + q * sl) / curTotal;
                a = (Math.sqrt(d2) / L) * (cross >= 0 ? 1 : -1);
                past = 0;
            }
        }
        double aa = Math.abs(a);
        double w = halfWidth(s);
        out.au = s;
        out.av = a;

        // Nothing reaches past the tail tip.
        if (past > 0) return false;

        // Pectoral fins at the shoulders, pelvic fins further back: see-through.
        boolean pect = s > 0.17 && s < 0.34 && aa < w + 0.1 * curFinBeat * Math.sin(((s - 0.17) / 0.17) * Math.PI);
        boolean pelvic = s > 0.48 && s < 0.6 && aa < w + 0.05 * Math.sin(((s - 0.48) / 0.12) * Math.PI);
        if (aa > w) return (pect || pelvic) && Ink.ink(out, FIN, Ink.GLASS, "-=");
        if (s > 0.82) {
            // A shallow V-notch at the very end of a forked tail.
            if (s > 0.93 && aa < ((s - 0.93) / 0.07) * 0.06) return false;
            return Ink.ink(out, FIN, Ink.GLASS, s > 0.9 ? "/\\" : "=");
        }

        double seed = curSeed;
        boolean patch = blotch(s, a, seed) > 0.25;
        double across = aa / w;
        switch (curVariety) {
            case KOHAKU:
                return patch && s < 0.8 ? Ink.ink(out, RED, Ink.SCALE) : Ink.ink(out, WHITE, Ink.SCALE);
            case SANKE:
                if (AMath.hash3(Math.round(s * 30), Math.round(a * 60), curId) > 0.9 && s > 0.15) {
                    return Ink.ink(out, BLACK, Ink.DARK);
                }
                return patch && s < 0.8 ? Ink.ink(out, RED, Ink.SCALE) : Ink.ink(out, WHITE, Ink.SCALE);
            case SHOWA:
                if (blotch(s, a, seed + 4) > 0.55) return Ink.ink(out, WHITE, Ink.SCALE);
                return patch ? Ink.ink(out, RED, Ink.SCALE) : Ink.ink(out, BLACK, Ink.DARK, "%#");
            case OGON:
                return Ink.ink(out, GOLD, Ink.SHIMMER);
            default:
                // Chagoi.
                return Ink.ink(out, ORANGE[0] * (1 - 0.15 * across), ORANGE[1] * (1 - 0.15 * across), ORANGE[2],
                        Ink.SHIMMER, null);
        }
    }
}

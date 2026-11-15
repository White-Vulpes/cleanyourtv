package com.gathin.tvhome.ascii;

import java.util.ArrayList;

/**
 * Port of ascii-animals src/flock.ts: the shared world every animal lives in. A population crosses the screen
 * along one course and re-enters upstream once it leaves downstream. Size stands in for distance: each creature
 * has a fixed random roll that maps to a depth (0 far, 1 near); depth scales its size, speed and fade. The list
 * stays sorted far to near, so near creatures draw over far ones.
 */
public final class Flock<C extends Creature> {
    /** Something stamped into the trail field (a splash, a startle), radius in cells. */
    public static final class WorldEvent {
        public final double x, y, amount;
        public final int radius;

        public WorldEvent(double x, double y, int radius, double amount) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.amount = amount;
        }
    }

    private static final double STARTLE_RADIUS = 240;
    /** Size of the nearest individuals (before an animal's own scale) on a laptop-sized screen, px. */
    private static final double REFERENCE_SIZE = 160;

    public final Animal<C> animal;
    public final Tuning tuning;
    public double width, height, time;
    public final ArrayList<C> creatures = new ArrayList<>();
    public final ArrayList<WorldEvent> events = new ArrayList<>();
    /** Size of the nearest individuals at size 1, px. */
    public double base = 120;
    /** Scratch acceleration the steering helpers add into. */
    public final double[] acc = new double[2];

    private int nextId = 1;
    private final int count;

    /** count > 0 pins the population, overriding tuning.density. */
    public Flock(Animal<C> animal, Tuning tuning, int count) {
        this.animal = animal;
        this.tuning = tuning;
        this.count = count;
    }

    public void resize(double width, double height) {
        boolean first = this.width == 0;
        this.width = width;
        this.height = height;
        base = AMath.clamp(Math.min(width, height) * 0.2, 80, 190) * animal.scale;
        if (first) populate(true);
    }

    /** Course in radians (screen space, y down). */
    public double course() {
        return tuning.corner ? Math.atan2(-height, width) : (-tuning.direction * Math.PI) / 180;
    }

    /** The course with the whole stream's slow sway applied. */
    public double heading() {
        double t = time;
        return course() + tuning.sway * (Math.sin(t * 0.13) + 0.5 * Math.sin(t * 0.31 + 1.7));
    }

    /** How many to keep on screen, by how much of the screen they cover. */
    public int target() {
        if (count > 0) return count;
        double shrink = Math.pow(base / animal.scale / REFERENCE_SIZE, 2);
        return AMath.clamp((int) Math.round(((width * height) / (animal.area * shrink)) * tuning.density), 1,
                animal.max);
    }

    public double depthOf(double roll) {
        return tuning.farthest + (1 - tuning.farthest) * Math.pow(roll, tuning.farBias);
    }

    /** Parallax: far individuals cover less screen per second. */
    public double cruise(Creature c) {
        return animal.speed * (0.55 + 0.45 * c.depth) * tuning.speed;
    }

    /** Everything near (x, y) reacts, then rejoins the stream. */
    public void startle(double x, double y) {
        events.add(new WorldEvent(x, y, 6, 1.4));
        for (C c : creatures) {
            double dx = c.x - x;
            double dy = c.y - y;
            double d = Math.hypot(dx, dy);
            if (d == 0) d = 1;
            if (d > STARTLE_RADIUS) continue;
            double strength = 1 - d / STARTLE_RADIUS;
            if (!animal.startle(c, dx / d, dy / d, strength, this)) {
                double k = strength * 420 * c.depth;
                c.vx += (dx / d) * k;
                c.vy += (dy / d) * k;
                c.startledUntil = time + AMath.rand(0.6, 1.2);
            }
        }
    }

    public void step(double dt) {
        time += dt;
        populate(false);
        for (int i = 0, n = creatures.size(); i < n; i++) {
            C c = creatures.get(i);
            c.depth = depthOf(c.roll);
            c.span = base * c.depth * tuning.size;
        }
        animal.prepare(this, dt);
        for (int i = 0; i < creatures.size(); i++) animal.update(creatures.get(i), this, dt);
    }

    /** Add or remove individuals until the population matches the tuning. */
    private void populate(boolean scatter) {
        int target = target();
        while (creatures.size() > target) {
            C c = creatures.remove((int) (AMath.random() * creatures.size()));
            c.gone = true;
        }
        while (creatures.size() < target) {
            C c = spawn();
            if (scatter || AMath.random() < 0.5) {
                // Seeding (or growing the flock): drop it in anywhere so the screen fills at once.
                c.x = AMath.rand(0, width);
                c.y = AMath.rand(0, height);
                animal.enter(c, this);
            }
            insert(c);
        }
    }

    /** Keep farther individuals first so the near ones draw over them. */
    public void insert(C c) {
        int at = creatures.size();
        for (int i = 0; i < creatures.size(); i++) {
            if (creatures.get(i).roll > c.roll) {
                at = i;
                break;
            }
        }
        creatures.add(at, c);
    }

    private C spawn() {
        C c = animal.newCreature();
        c.id = nextId++;
        c.roll = AMath.random();
        c.depth = depthOf(c.roll);
        c.span = base * c.depth * tuning.size;
        animal.init(c, this);
        reenter(c);
        return c;
    }

    /** Bring a creature back in from the upstream edges, spread evenly across the flow. */
    public void reenter(C c) {
        double W = width, H = height, course = course();
        double m = c.span * 0.6;
        double cos = Math.cos(course);
        double sin = Math.sin(course);
        double sideways = H * Math.abs(cos);
        double vertical = W * Math.abs(sin);
        if (AMath.random() * (sideways + vertical) < sideways) {
            c.x = cos >= 0 ? -m : W + m;
            c.y = AMath.rand(0, H);
        } else {
            c.x = AMath.rand(0, W);
            c.y = sin <= 0 ? H + m : -m;
        }
        double cruise = cruise(c);
        c.heading = course;
        c.vx = cos * cruise;
        c.vy = sin * cruise;
        c.bias = AMath.rand(-1, 1);
        animal.enter(c, this);
    }

    /** Past the downstream edges (done crossing), or lost far off any edge. margin defaults to c.span. */
    public boolean passed(Creature c) {
        return passed(c, c.span);
    }

    public boolean passed(Creature c, double margin) {
        double W = width, H = height, course = course();
        double cos = Math.cos(course);
        double sin = Math.sin(course);
        boolean out = (cos > 0.05 && c.x > W + margin) || (cos < -0.05 && c.x < -margin)
                || (sin < -0.05 && c.y < -margin) || (sin > 0.05 && c.y > H + margin);
        boolean lost = c.x < -3 * margin || c.x > W + 3 * margin || c.y < -3 * margin || c.y > H + 3 * margin;
        return out || lost;
    }

    // steering helpers: each adds into acc

    /** Clear the scratch acceleration. */
    public double[] begin() {
        acc[0] = 0;
        acc[1] = 0;
        return acc;
    }

    /** Pull the velocity toward speed along dir, as hard as grip. */
    public void steer(Creature c, double dir, double speed, double grip) {
        acc[0] += (Math.cos(dir) * speed - c.vx) * grip;
        acc[1] += (Math.sin(dir) * speed - c.vy) * grip;
    }

    /** Push away from neighbours at about the same depth (strength = tuning.separation, range = 0.42). */
    public void separate(Creature c) {
        separate(c, tuning.separation, 0.42);
    }

    public void separate(Creature c, double strength) {
        separate(c, strength, 0.42);
    }

    public void separate(Creature c, double strength, double range) {
        if (strength <= 0) return;
        for (int i = 0, n = creatures.size(); i < n; i++) {
            Creature o = creatures.get(i);
            if (o == c || Math.abs(o.depth - c.depth) > 0.18) continue;
            double ox = c.x - o.x;
            double oy = c.y - o.y;
            double d = Math.hypot(ox, oy);
            double r = (c.span + o.span) * range;
            if (d < r && d > 1e-3) {
                double w = (1 - d / r) * strength;
                acc[0] += (ox / d) * w;
                acc[1] += (oy / d) * w;
            }
        }
    }

    /** Apply acc, cap the speed (startled creatures may go faster), move. Returns the speed. */
    public double move(Creature c, double dt, double max) {
        c.vx += acc[0] * dt;
        c.vy += acc[1] * dt;
        double speed = Math.hypot(c.vx, c.vy);
        double cap = max + (time < c.startledUntil ? 300 : 0);
        if (speed > cap) {
            c.vx *= cap / speed;
            c.vy *= cap / speed;
            speed = cap;
        }
        c.x += c.vx * dt;
        c.y += c.vy * dt;
        return speed;
    }

    /** Turn to face the direction of travel, at most rate rad/s (rate 7 by default). */
    public void face(Creature c, double dt) {
        face(c, dt, 7, Math.atan2(c.vy, c.vx));
    }

    public void face(Creature c, double dt, double rate) {
        face(c, dt, rate, Math.atan2(c.vy, c.vx));
    }

    public void face(Creature c, double dt, double rate, double angle) {
        double turn = rate * dt;
        c.heading += AMath.clamp(AMath.angleDiff(c.heading, angle), -turn, turn);
    }
}

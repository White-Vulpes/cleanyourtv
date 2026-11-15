package com.gathin.tvhome.ascii;

/**
 * Port of the Animal interface in flock.ts. Differences from the original:
 * <ul>
 *   <li>create(c, flock) is split in two: {@link #newCreature()} returns a fresh instance of the animal's own
 *       Creature subclass, the flock fills in the shared fields, then {@link #init} sets the animal's own.</li>
 *   <li>defaults is {@link #defaults(Tuning)}, which changes shared fields and puts the animal's own knobs.</li>
 *   <li>No controls, hide or tap: the TV has no tweak panel and no pointer.</li>
 * </ul>
 */
public abstract class Animal<C extends Creature> {
    public final String key;
    public final String label;
    /** Screen area per individual at density 1 on a laptop-sized screen, px squared. */
    public final double area;
    /** Most individuals the flock will hold. */
    public final int max;
    /** Size of the nearest at size 1, relative to the shared base. */
    public final double scale;
    /** Cruising speed of the nearest at speed 1, px/s. */
    public final double speed;
    /** Trail colour on the dark theme (the TV only uses dark). */
    public final double[] trail;

    protected Animal(String key, String label, double area, int max, double scale, double speed, double[] trail) {
        this.key = key;
        this.label = label;
        this.area = area;
        this.max = max;
        this.scale = scale;
        this.speed = speed;
        this.trail = trail;
    }

    /** Start from SHARED_TUNING (already in t) and apply this animal's defaults. */
    public abstract void defaults(Tuning t);

    /** A fresh instance of this animal's Creature subclass, with nothing set yet. */
    public abstract C newCreature();

    /** The rest of create(): set this animal's own fields. The shared fields are already filled in. */
    public void init(C c, Flock<C> flock) {}

    /** A creature has just been placed at the upstream edge (or anywhere, when seeding). */
    public void enter(C c, Flock<C> flock) {}

    /** Once per step, before any individual moves. */
    public void prepare(Flock<C> flock, double dt) {}

    public abstract void update(C c, Flock<C> flock, double dt);

    /** React to a startle; (dx, dy) points away from it and strength fades 1 to 0. Default handled by Flock. */
    public boolean startle(C c, double dx, double dy, double strength, Flock<C> flock) {
        return false;
    }

    /** Draw one creature (used when {@link #drawsAll()} is false). */
    public void draw(C c, Pen pen, Flock<C> flock) {}

    /** true: {@link #drawAll} draws the whole flock instead of draw() per creature. */
    public boolean drawsAll() {
        return false;
    }

    public void drawAll(Flock<C> flock, Pen pen) {}
}

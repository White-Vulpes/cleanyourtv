package com.gathin.tvhome.ascii;

/** Port of Pose in renderer.ts: where and how big something is drawn, a creature or one part of it. */
public final class Pose {
    public double x, y, heading;
    /** px per unit of body coordinates. */
    public double span;
    public double depth;
    /** Seeds the glyph texture. */
    public int id;

    public Pose() {}

    public Pose(double x, double y, double heading, double span, double depth, int id) {
        set(x, y, heading, span, depth, id);
    }

    public Pose set(double x, double y, double heading, double span, double depth, int id) {
        this.x = x;
        this.y = y;
        this.heading = heading;
        this.span = span;
        this.depth = depth;
        this.id = id;
        return this;
    }

    /** The pose of a creature as it stands: { x, y, heading, span, depth, id } of c. */
    public Pose of(Creature c) {
        return set(c.x, c.y, c.heading, c.span, c.depth, c.id);
    }
}

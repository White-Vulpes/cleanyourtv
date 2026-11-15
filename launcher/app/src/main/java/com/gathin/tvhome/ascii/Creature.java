package com.gathin.tvhome.ascii;

/** Port of the Creature interface in flock.ts. Animals extend it with their own fields. */
public class Creature {
    public int id;
    /** Fixed per creature; depth is derived from it with the current tuning. */
    public double roll;
    /** 0 far / faded, 1 near / vivid. */
    public double depth = 1;
    /** Size of this creature in px (wingspan, body length: whatever its shape is measured in). */
    public double span;
    public double x, y, vx, vy, heading;
    /** Personal deviation from the course, -1 to 1 (scaled by tuning.spread). */
    public double bias;
    public double startledUntil;
    /** Removed from the flock; anyone holding a reference should let go. */
    public boolean gone;
}

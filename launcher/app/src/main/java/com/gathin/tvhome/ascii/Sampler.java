package com.gathin.tvhome.ascii;

/**
 * Port of Sampler in renderer.ts. Classifies one point in body coordinates (fractions of span: u forward along
 * the heading, v across to the right) and its screen position (px, py). On a hit, write pen.ink and return true.
 * Runs five times per covered cell per frame: no allocation and no map lookups inside.
 */
public interface Sampler {
    boolean sample(double u, double v, double px, double py);
}

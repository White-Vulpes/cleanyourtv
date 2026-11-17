package com.gathin.tvhome.ascii;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A banner in big block letters across the whole screen ("HAPPY BIRTHDAY MAU") with fireworks bursting behind it
 * without a break: rockets climb from the bottom on a sparking trail, burst into a ring of sparks that fall, fade
 * and crackle. Drawn on the same ASCII grid as the animals. One placeholder creature keeps the Flock happy; the
 * fireworks are a particle system of their own, stepped in prepare().
 */
public final class Fireworks extends Animal<Creature> {
    private static final double[][] PALETTE = {
        {255, 92, 120}, {255, 196, 64}, {90, 220, 255}, {150, 255, 140}, {200, 120, 255}, {255, 140, 60},
        {255, 255, 255},
    };
    private static final String SPARKS = "*+:.";
    private static final double GRAVITY = 90;   // px/s^2

    private final String banner;
    private final List<Particle> parts = new ArrayList<>();
    /** The last few bursts, which light up the letters near them for a moment. */
    private final Flash[] flashes = new Flash[8];
    private int nextFlash;

    private static final class Flash {
        double x, y, at = -100;
        double[] colour = {255, 255, 255};
    }
    private double nextLaunch;

    /** A rocket (climbing) or a spark (after a burst). */
    private static final class Particle {
        double x, y, vx, vy, age, life, burstAt;
        double[] colour;
        boolean rocket, crackle;
    }

    public Fireworks(String banner) {
        super("fireworks", "Fireworks", 1e12, 1, 1, 1, new double[] {255, 200, 120});
        this.banner = banner == null ? "" : banner.toUpperCase(Locale.ROOT).trim();
        for (int i = 0; i < flashes.length; i++) flashes[i] = new Flash();
    }

    @Override
    public void defaults(Tuning t) {
        t.trail = 1.2;
        t.trailFade = 1.6;
        t.grid = 0.9;
        t.detail = 1.3;
        t.sparkle = 0.18;
    }

    @Override
    public Creature newCreature() {
        return new Creature();
    }

    @Override
    public void update(Creature c, Flock<Creature> flock, double dt) {
        c.x = -10000;   // the placeholder stays far off screen
        c.y = -10000;
    }

    @Override
    public void prepare(Flock<Creature> flock, double dt) {
        double W = flock.width, H = flock.height, t = flock.time;
        // A new rocket every 0.25 to 0.9 s, sometimes a salvo of three.
        if (t >= nextLaunch && W > 0) {
            int n = AMath.random() < 0.2 ? 3 : 1;
            for (int i = 0; i < n; i++) launch(W, H);
            nextLaunch = t + AMath.rand(0.25, 0.9);
        }
        for (int i = parts.size() - 1; i >= 0; i--) {
            Particle p = parts.get(i);
            p.age += dt;
            if (p.rocket) {
                p.vy += GRAVITY * 0.35 * dt;
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                if (p.age >= p.burstAt || p.vy > -40) {
                    burst(p, flock);
                    parts.remove(i);
                }
                continue;
            }
            double drag = Math.exp(-1.6 * dt);
            p.vx *= drag;
            p.vy = p.vy * drag + GRAVITY * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (p.age >= p.life || p.y > H + 40) parts.remove(i);
        }
    }

    private void launch(double W, double H) {
        Particle r = new Particle();
        r.rocket = true;
        r.x = AMath.rand(W * 0.08, W * 0.92);
        r.y = H + 10;
        r.vx = AMath.rand(-40, 40);
        r.vy = -AMath.rand(H * 0.75, H * 1.05);      // bursts between a fifth and two thirds up the screen
        r.burstAt = AMath.rand(0.8, 1.35);
        r.colour = PALETTE[(int) (AMath.random() * PALETTE.length)];
        parts.add(r);
    }

    private void burst(Particle r, Flock<Creature> flock) {
        int n = 80 + (int) (AMath.random() * 50);
        double speed = AMath.rand(180, 320);
        boolean ring = AMath.random() < 0.35;
        double[] second = AMath.random() < 0.4 ? PALETTE[(int) (AMath.random() * PALETTE.length)] : r.colour;
        for (int i = 0; i < n; i++) {
            Particle s = new Particle();
            double a = AMath.TAU * i / n + AMath.rand(-0.05, 0.05);
            double v = ring ? speed * AMath.rand(0.92, 1.0) : speed * Math.sqrt(AMath.random());
            s.x = r.x;
            s.y = r.y;
            s.vx = Math.cos(a) * v;
            s.vy = Math.sin(a) * v;
            s.life = AMath.rand(1.6, 2.8);
            s.colour = i % 2 == 0 ? r.colour : second;
            s.crackle = AMath.random() < 0.25;
            parts.add(s);
        }
        flock.events.add(new Flock.WorldEvent(r.x, r.y, 6, 1.6));   // a flash of glow where it burst
        Flash f = flashes[nextFlash];
        nextFlash = (nextFlash + 1) % flashes.length;
        f.x = r.x;
        f.y = r.y;
        f.at = flock.time;
        f.colour = r.colour;
    }

    @Override
    public boolean drawsAll() {
        return true;
    }

    @Override
    public void drawAll(Flock<Creature> flock, Pen pen) {
        double t = flock.time;
        double cw = pen.cw();
        for (Particle p : parts) {
            if (p.rocket) {
                pen.spot(p.x, p.y, "*", WHITE, p.colour, 1);
                pen.dot(p.x - p.vx * 0.03, p.y - p.vy * 0.03, ":", p.colour, 1, 0.1);
                pen.dot(p.x - p.vx * 0.06, p.y - p.vy * 0.06, ".", p.colour, 1, 0.4);
                pen.shed(p.x, p.y + cw, 0.5);
                continue;
            }
            double f = p.age / p.life;
            if (p.crackle && f > 0.55 && AMath.hash3(p.x, p.y, Math.floor(t * 12)) < 0.5) continue;   // twinkle out
            String g = String.valueOf(SPARKS.charAt(AMath.clamp((int) (f * SPARKS.length()), 0, SPARKS.length() - 1)));
            if (f < 0.45) {
                // Young sparks burn: a solid cell of the colour with a white-hot glyph.
                dim[0] = p.colour[0] * (0.95 - f);
                dim[1] = p.colour[1] * (0.95 - f);
                dim[2] = p.colour[2] * (0.95 - f);
                pen.spot(p.x, p.y, f < 0.15 ? "@" : "*", f < 0.2 ? WHITE : p.colour, dim, 1);
            } else {
                pen.dot(p.x, p.y, g, p.colour, 1, Math.min(0.9, (f - 0.45) * 1.4));
            }
            if (f < 0.6) pen.shed(p.x, p.y, 0.14);
        }
        drawBanner(pen, t);
    }

    private static final double[] WHITE = {255, 255, 255};
    /** Scratch colour for the banner: spot() copies it at once, so one array serves every cell. */
    private final double[] rgb = new double[3];
    /** Scratch fill for a burning spark; spot() copies it at once. */
    private final double[] dim = new double[3];
    private static final double[] GLYPH_DARK = {70, 42, 12};
    private static final double[] GLYPH_GLINT = {255, 246, 220};
    private static final double[] GOLD = {214, 160, 62};
    private static final double[] PALE = {255, 236, 180};
    /** How strongly the glint covers the cell gold() just coloured (0 to 1). */
    private double glint;

    /**
     * Gilded letters: gold with a slight grain, a soft band of light sweeping across every few seconds, and the
     * colour of any firework that just burst nearby, fading back to gold over a second.
     */
    private double[] gold(int c, int r, double px, double py, double t, int cols) {
        double grain = 0.82 + 0.18 * AMath.hash3(c, r, 11);
        double R = GOLD[0] * grain, G = GOLD[1] * grain, B = GOLD[2] * grain;
        // The glint: a diagonal band crossing the whole width every 4.5 s, then a pause.
        double period = 6.0, travel = 4.5;
        double phase = (t % period) / travel;            // 0..1 while it crosses, >1 during the pause
        double pos = phase * (cols + 40) - 20;           // the band's column
        double d = Math.abs(c + r * 0.6 - pos);
        glint = phase <= 1 ? Math.max(0, 1 - d / 5.0) : 0;
        R += (PALE[0] - R) * glint;
        G += (PALE[1] - G) * glint;
        B += (PALE[2] - B) * glint;
        // Firework light: within about 420 px of a burst, for 1.2 s after it.
        for (Flash f : flashes) {
            double age = t - f.at;
            if (age < 0 || age > 1.2) continue;
            double dist = Math.hypot(px - f.x, py - f.y);
            if (dist > 420) continue;
            double k = (1 - age / 1.2) * (1 - dist / 420) * 0.85;
            R += (f.colour[0] - R) * k;
            G += (f.colour[1] - G) * k;
            B += (f.colour[2] - B) * k;
        }
        rgb[0] = R;
        rgb[1] = G;
        rgb[2] = B;
        return rgb;
    }

    /** The banner in a 5x7 font, each font pixel a block of cells about as wide as it is tall, on top of the sparks. */
    private void drawBanner(Pen pen, double t) {
        if (banner.isEmpty()) return;
        int cols = pen.cols(), rows = pen.rows();
        double cw = pen.cw(), ch = pen.ch();
        Layout lay = layout(cols, rows, cw, ch);
        if (lay == null) return;
        int sx = lay.sx, sy = lay.sy;
        int lineH = 7 * sy, gap = 2 * sy;
        int total = lay.lines.size() * lineH + (lay.lines.size() - 1) * gap;
        int top = (rows - total) / 2;
        String glyphs = "#@%8";
        for (int li = 0; li < lay.lines.size(); li++) {
            String line = lay.lines.get(li);
            int width = textWidth(line) * sx;
            int left = (cols - width) / 2;
            int y0 = top + li * (lineH + gap);
            int cx = left;
            for (int k = 0; k < line.length(); k++) {
                String[] rowsOf = glyph(line.charAt(k));
                for (int gy = 0; gy < 7; gy++) {
                    for (int gx = 0; gx < 5; gx++) {
                        if (rowsOf[gy].charAt(gx) != '#') continue;
                        for (int dy = 0; dy < sy; dy++) {
                            for (int dx = 0; dx < sx; dx++) {
                                int c = cx + gx * sx + dx, r = y0 + gy * sy + dy;
                                if (c < 0 || r < 0 || c >= cols || r >= rows) continue;
                                double[] fill = gold(c, r, (c + 0.5) * cw, (r + 0.5) * ch, t, cols);
                                char gch = glyphs.charAt((int) (AMath.hash3(c, r, Math.floor(t * 3)) * glyphs.length()));
                                pen.spot((c + 0.5) * cw, (r + 0.5) * ch, String.valueOf(gch),
                                        glint > 0.5 ? GLYPH_GLINT : GLYPH_DARK, fill, 1);
                            }
                        }
                    }
                }
                cx += 6 * sx;
            }
        }
    }

    private static final class Layout {
        int sx, sy;
        List<String> lines;
    }

    private Layout cached;
    private int cachedCols, cachedRows;

    /** The biggest scale at which the words, wrapped onto lines, fit in 94% of the width and 86% of the height. */
    private Layout layout(int cols, int rows, double cw, double ch) {
        if (cached != null && cachedCols == cols && cachedRows == rows) return cached;
        String[] words = banner.split("\\s+");
        for (int sy = 6; sy >= 1; sy--) {
            int sx = Math.max(1, (int) Math.round(sy * ch / cw));   // square font pixels
            List<String> lines = new ArrayList<>();
            String cur = "";
            boolean fits = true;
            for (String w : words) {
                String next = cur.isEmpty() ? w : cur + " " + w;
                if (textWidth(next) * sx <= cols * 0.94) {
                    cur = next;
                } else {
                    if (!cur.isEmpty()) lines.add(cur);
                    cur = w;
                    if (textWidth(w) * sx > cols * 0.94) fits = false;
                }
            }
            if (!cur.isEmpty()) lines.add(cur);
            int h = lines.size() * 7 * sy + (lines.size() - 1) * 2 * sy;
            if (fits && h <= rows * 0.86) {
                Layout l = new Layout();
                l.sx = sx;
                l.sy = sy;
                l.lines = lines;
                cached = l;
                cachedCols = cols;
                cachedRows = rows;
                return l;
            }
        }
        return null;
    }

    /** Width in font pixels: 5 per letter and 1 between letters. */
    private static int textWidth(String s) {
        return s.isEmpty() ? 0 : s.length() * 6 - 1;
    }

    private static final String[] BLANK = {".....", ".....", ".....", ".....", ".....", ".....", "....."};

    /** A 5x7 glyph: letters, digits and a little punctuation; anything else is blank. */
    private static String[] glyph(char c) {
        switch (c) {
            case 'A': return new String[] {".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"};
            case 'B': return new String[] {"####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."};
            case 'C': return new String[] {".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."};
            case 'D': return new String[] {"####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."};
            case 'E': return new String[] {"#####", "#....", "#....", "####.", "#....", "#....", "#####"};
            case 'F': return new String[] {"#####", "#....", "#....", "####.", "#....", "#....", "#...."};
            case 'G': return new String[] {".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####"};
            case 'H': return new String[] {"#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"};
            case 'I': return new String[] {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"};
            case 'J': return new String[] {"..###", "...#.", "...#.", "...#.", "#..#.", "#..#.", ".##.."};
            case 'K': return new String[] {"#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"};
            case 'L': return new String[] {"#....", "#....", "#....", "#....", "#....", "#....", "#####"};
            case 'M': return new String[] {"#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"};
            case 'N': return new String[] {"#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"};
            case 'O': return new String[] {".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."};
            case 'P': return new String[] {"####.", "#...#", "#...#", "####.", "#....", "#....", "#...."};
            case 'Q': return new String[] {".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"};
            case 'R': return new String[] {"####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"};
            case 'S': return new String[] {".####", "#....", "#....", ".###.", "....#", "....#", "####."};
            case 'T': return new String[] {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."};
            case 'U': return new String[] {"#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."};
            case 'V': return new String[] {"#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."};
            case 'W': return new String[] {"#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"};
            case 'X': return new String[] {"#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"};
            case 'Y': return new String[] {"#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."};
            case 'Z': return new String[] {"#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"};
            case '0': return new String[] {".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."};
            case '1': return new String[] {"..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."};
            case '2': return new String[] {".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"};
            case '3': return new String[] {"####.", "....#", "....#", ".###.", "....#", "....#", "####."};
            case '4': return new String[] {"...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."};
            case '5': return new String[] {"#####", "#....", "####.", "....#", "....#", "#...#", ".###."};
            case '6': return new String[] {".###.", "#....", "#....", "####.", "#...#", "#...#", ".###."};
            case '7': return new String[] {"#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."};
            case '8': return new String[] {".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."};
            case '9': return new String[] {".###.", "#...#", "#...#", ".####", "....#", "....#", ".###."};
            case '!': return new String[] {"..#..", "..#..", "..#..", "..#..", "..#..", ".....", "..#.."};
            case '&': return new String[] {".##..", "#..#.", "#.#..", ".#...", "#.#.#", "#..#.", ".##.#"};
            case '-': return new String[] {".....", ".....", ".....", "#####", ".....", ".....", "....."};
            case '.': return new String[] {".....", ".....", ".....", ".....", ".....", ".##..", ".##.."};
            case '\'': return new String[] {"..#..", "..#..", ".#...", ".....", ".....", ".....", "....."};
            case ',': return new String[] {".....", ".....", ".....", ".....", ".##..", "..#..", ".#..."};
            default: return BLANK;
        }
    }
}

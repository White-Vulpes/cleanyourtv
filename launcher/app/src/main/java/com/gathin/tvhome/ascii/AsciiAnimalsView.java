package com.gathin.tvhome.ascii;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;

/**
 * Animals drawn as ASCII, crossing the screen behind Home: a port of ascii-animals by drewdi (MIT,
 * https://github.com/DesignerStephen1/ascii-animals), engine after ascii-sharks. Only the animals: the page's
 * chat box and tweak panel are left out.
 *
 * Everything happens on its own thread, never on the UI thread that handles the remote: a SurfaceView behind the
 * window, simulated with a fixed 1/60 s step on that thread's frame clock and drawn through a hardware canvas at
 * most {@code fps} times a second. It runs only between {@link #start()} and {@link #stop()} (Home resumed /
 * paused), so nothing runs while an app or an HDMI input is on screen. Several animals take turns, each for
 * {@code minutes}, with a short fade through the background.
 */
public final class AsciiAnimalsView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "AsciiAnimals";
    private static final double STEP = 1 / 60.0;
    private static final int MAX_STEPS = 4;
    private static final long FADE_MS = 900;
    private static final long STATS_MS = 10_000;

    private final List<String> keys = new ArrayList<>();
    private final long rotateMs;
    private final long frameNs;
    private final HandlerThread thread;
    private final Handler handler;
    /** Guards the surface between the UI thread's callbacks and the render thread's draws. */
    private final Object surfaceLock = new Object();
    private boolean surfaceReady;

    // Render-thread state from here on.
    private final Renderer renderer = new Renderer();

    public AsciiAnimalsView(Context context, String animals, int minutes, int fps) {
        super(context);
        for (String k : animals.split(",")) {
            if (Animals.create(k) != null) keys.add(k.trim());
        }
        rotateMs = minutes <= 0 ? 0 : minutes * 60_000L;
        frameNs = 1_000_000_000L / Math.max(10, Math.min(60, fps));
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        getHolder().setFormat(PixelFormat.OPAQUE);
        getHolder().addCallback(this);
        thread = new HandlerThread("ascii-animals", Process.THREAD_PRIORITY_DISPLAY + 2);
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    /** True when at least one known animal was named. */
    public boolean hasAnimals() {
        return !keys.isEmpty();
    }

    /** Home is on screen: run. */
    public void start() {
        if (!keys.isEmpty()) handler.post(renderer::start);
    }

    /** Home is gone: stop simulating and drawing. */
    public void stop() {
        handler.post(renderer::stop);
    }

    /** Memory pressure: drop the grid and the flock; start() builds them again. */
    public void release() {
        handler.post(renderer::release);
    }

    /**
     * Birthdays: show fireworks behind this text (for example "Happy Birthday Mau") instead of the animals, until
     * it is set back to null. Safe to call every minute: the same text changes nothing.
     */
    public void setBanner(String text) {
        handler.post(() -> renderer.banner(text));
    }

    /** The activity is going away: stop and end the thread. */
    public void close() {
        handler.post(renderer::stop);
        thread.quitSafely();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        synchronized (surfaceLock) {
            surfaceReady = true;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        handler.post(() -> renderer.resize(width, height));
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Returning lets the system take the surface away, so no draw may be in flight after this.
        synchronized (surfaceLock) {
            surfaceReady = false;
        }
    }

    /** Lives on the render thread. */
    private final class Renderer implements Choreographer.FrameCallback {
        private final Paint fill = new Paint();
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridPaint = new Paint();
        private final Paint trailPaint = new Paint();
        private final Paint shade = new Paint();
        private final int[] trailArgb = new int[AsciiGrid.ALPHA_LEVELS];
        private final int background = 0xFF000000 | AsciiGrid.pack(AsciiGrid.BACKGROUND[0],
                AsciiGrid.BACKGROUND[1], AsciiGrid.BACKGROUND[2]);

        private Tuning tuning = new Tuning();
        private final AsciiGrid grid = new AsciiGrid(tuning);
        @SuppressWarnings("rawtypes")
        private Flock flock;
        private int index = -1;
        /** Birthday text shown with fireworks instead of the animals; null for the animals. */
        private String banner;
        private long animalSince;
        private float[] gridLines = new float[0];
        private float baseline;
        private int width, height;

        private boolean running;
        private long lastNs, lastDrawNs;
        private double acc;
        private long fadeStart = -1;
        private boolean switched;
        private float shadeAlpha;

        // timing, logged every STATS_MS
        private long statsSince, simNs, rasterNs, drawNs;
        private int statsFrames;
        private long statsCells;

        Renderer() {
            text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            text.setTextAlign(Paint.Align.CENTER);
            gridPaint.setStrokeWidth(1);
            fill.setStyle(Paint.Style.FILL);
            trailPaint.setStyle(Paint.Style.FILL);
        }

        void start() {
            if (running) return;
            running = true;
            if (flock == null) {
                if (banner != null) useFireworks(banner);
                else useAnimal(Math.max(0, index));
            }
            lastNs = 0;
            Choreographer.getInstance().postFrameCallback(this);
        }

        void stop() {
            running = false;
            Choreographer.getInstance().removeFrameCallback(this);
        }

        void release() {
            stop();
            grid.release();
            flock = null;
            atlas = atlasArgb = null;
            atlasCanvas = null;
        }

        /** Fireworks behind text, or (null) back to the animal that was showing. */
        void banner(String text) {
            String t = text == null || text.trim().isEmpty() ? null : text.trim();
            if (t == null ? banner == null : t.equals(banner)) return;
            banner = t;
            fadeStart = -1;
            shadeAlpha = 0;
            if (flock == null) return;   // start() picks it up
            if (t != null) useFireworks(t);
            else useAnimal(Math.max(0, index));
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void useFireworks(String text) {
            Animal<?> animal = new Fireworks(text);
            tuning = new Tuning();
            animal.defaults(tuning);
            flock = new Flock(animal, tuning, 0);
            grid.tuning = tuning;
            grid.setAnimal(animal, tuning);
            layoutGrid();
        }

        void resize(int w, int h) {
            width = w;
            height = h;
            layoutGrid();
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void useAnimal(int i) {
            index = i % keys.size();
            Animal<?> animal = Animals.create(keys.get(index));
            tuning = new Tuning();
            animal.defaults(tuning);
            flock = new Flock(animal, tuning, 0);
            grid.tuning = tuning;
            grid.setAnimal(animal, tuning);
            animalSince = SystemClock.uptimeMillis();
            layoutGrid();
        }

        private void layoutGrid() {
            int w = width, h = height;
            if (w < 1 || h < 1 || flock == null) return;
            grid.resize(w, h);
            flock.resize(w, h);
            int nc = (int) Math.ceil(w / grid.cw) + 1, nr = (int) Math.ceil(h / grid.ch) + 1;
            gridLines = new float[(nc + nr) * 4];
            int k = 0;
            for (int c = 0; c < nc; c++) {
                float x = Math.round(c * grid.cw) + 0.5f;
                gridLines[k++] = x; gridLines[k++] = 0; gridLines[k++] = x; gridLines[k++] = h;
            }
            for (int r = 0; r < nr; r++) {
                float y = Math.round(r * grid.ch) + 0.5f;
                gridLines[k++] = 0; gridLines[k++] = y; gridLines[k++] = w; gridLines[k++] = y;
            }
            int ga = (int) Math.round(255 * Math.min(1, AsciiGrid.GRID[3] * tuning.grid));
            gridPaint.setColor((ga << 24) | AsciiGrid.pack(AsciiGrid.GRID[0], AsciiGrid.GRID[1], AsciiGrid.GRID[2]));
            text.setTextSize(Math.round(grid.ch * 0.74));
            Paint.FontMetrics fm = text.getFontMetrics();
            baseline = (float) (grid.ch / 2 + 1) - (fm.ascent + fm.descent) / 2;
            buildAtlas();
            for (int l = 0; l < AsciiGrid.ALPHA_LEVELS; l++) {
                int a = (int) Math.round(255 * AsciiGrid.TRAIL_ALPHA * (l + 1) / AsciiGrid.ALPHA_LEVELS);
                trailArgb[l] = (a << 24) | grid.trailColor;
            }
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            if (!running) return;
            Choreographer.getInstance().postFrameCallback(this);
            if (flock == null || grid.cols == 0) {
                layoutGrid();
                return;
            }
            if (lastNs == 0) {
                lastNs = frameTimeNanos;
                lastDrawNs = frameTimeNanos - frameNs;
            }
            // Frame cap: skip vsyncs until a frame's worth of time has passed (2 ms slack for jitter).
            if (frameTimeNanos - lastDrawNs < frameNs - 2_000_000L) return;
            lastDrawNs = frameTimeNanos;
            double dt = Math.min(0.1, (frameTimeNanos - lastNs) / 1e9);
            lastNs = frameTimeNanos;
            rotate();

            long t0 = System.nanoTime();
            acc += dt;
            int steps = 0;
            while (acc >= STEP && steps < MAX_STEPS) {
                flock.step(STEP);
                acc -= STEP;
                steps++;
            }
            if (steps == MAX_STEPS) acc = 0;
            long t1 = System.nanoTime();
            grid.frame(flock, dt);
            long t2 = System.nanoTime();
            int cells = paint();
            long t3 = System.nanoTime();
            stats(t1 - t0, t2 - t1, t3 - t2, cells);
        }

        /** Hand over to the next animal with a short fade through the background. */
        private void rotate() {
            if (banner != null || rotateMs == 0 || keys.size() < 2) return;
            long now = SystemClock.uptimeMillis();
            if (fadeStart < 0) {
                if (now - animalSince < rotateMs) return;
                fadeStart = now;
                switched = false;
            }
            float f = (now - fadeStart) / (float) FADE_MS;
            if (f < 1) {
                shadeAlpha = f;
            } else if (!switched) {
                switched = true;
                shadeAlpha = 1;
                useAnimal(index + 1);
            } else if (f < 2) {
                shadeAlpha = 2 - f;
            } else {
                shadeAlpha = 0;
                fadeStart = -1;
            }
        }

        private int paint() {
            synchronized (surfaceLock) {
                if (!surfaceReady) return 0;
                SurfaceHolder holder = getHolder();
                Canvas canvas;
                try {
                    canvas = holder.lockHardwareCanvas();
                } catch (RuntimeException e) {
                    return 0;
                }
                if (canvas == null) return 0;
                int cells;
                try {
                    canvas.drawColor(background);
                    canvas.drawLines(gridLines, gridPaint);
                    drawTrail(canvas);
                    cells = drawCells(canvas);
                    if (shadeAlpha > 0) {
                        shade.setColor(((int) (255 * Math.min(1, shadeAlpha)) << 24) | (background & 0xFFFFFF));
                        canvas.drawRect(0, 0, width, height, shade);
                    }
                } finally {
                    holder.unlockCanvasAndPost(canvas);
                }
                return cells;
            }
        }

        private void drawTrail(Canvas canvas) {
            float[] energy = grid.energy;
            byte[] flags = grid.cellFlag;
            int cols = grid.cols;
            double cw = grid.cw, ch = grid.ch;
            double t = flock.time;
            int small = (int) Math.max(2, Math.round(cw * 0.19));
            for (int i = 0; i < energy.length; i++) {
                float e = energy[i];
                if (e < 0.12f || flags[i] == AsciiGrid.CELL_SOLID) continue;
                int col = i % cols;
                int row = i / cols;
                double twinkle = 0.75 + 0.25 * Math.sin(t * 5 + AMath.hash3(col, row, 3) * 40);
                int level = AMath.clamp((int) Math.floor(e * twinkle * 4 * AsciiGrid.ALPHA_LEVELS) - 1, 0,
                        AsciiGrid.ALPHA_LEVELS - 1);
                int size = e > 0.6f ? small + 1 : small;
                float x = Math.round(col * cw + cw * 0.5 - size / 2.0);
                float y = Math.round(row * ch + ch * 0.6 - size / 2.0);
                trailPaint.setColor(trailArgb[level]);
                canvas.drawRect(x, y, x + size, y + size, trailPaint);
            }
        }

        private int drawCells(Canvas canvas) {
            byte[] flags = grid.cellFlag;
            int[] fills = grid.cellFill, colors = grid.cellColor;
            char[] glyphs = grid.cellGlyph;
            int cols = grid.cols;
            float cw = (float) grid.cw, ch = (float) grid.ch;
            int n = Math.min(flags.length, cols * grid.rows);
            int drawn = 0;
            if (atlas == null) return 0;
            for (int i = 0; i < n; i++) {
                byte flag = flags[i];
                if (flag == AsciiGrid.CELL_NONE) continue;
                drawn++;
                float x = (i % cols) * cw;
                float y = (i / cols) * ch;
                if (flag == AsciiGrid.CELL_SOLID) {
                    fill.setColor(0xFF000000 | fills[i]);
                    canvas.drawRect(x + 1, y + 1, x + cw, y + ch, fill);
                }
                char g = glyphs[i];
                if (g == ' ' || g == 0) continue;
                int s = slot(g);
                if (s < 0) continue;
                int sx = (s % ATLAS_COLS) * cellW, sy = (s / ATLAS_COLS) * cellH;
                src.set(sx, sy, sx + cellW, sy + cellH);
                dst.set(x, y, x + cellW, y + cellH);
                glyphPaint.setColor(0xFF000000 | colors[i]);
                canvas.drawBitmap(atlas, src, dst, glyphPaint);
            }
            return drawn;
        }

        // Glyph atlas: every character drawn once, white on transparent, into one alpha-only bitmap. A cell is then
        // a bitmap copy tinted by the paint colour (an ALPHA_8 bitmap takes the paint's colour), which costs a
        // fraction of a drawText call and lets the GPU batch the whole frame.
        private static final int ATLAS_COLS = 16;
        private static final int ATLAS_SLOTS = 256;
        private static final String PRESET = " .:;+=*%#@8o',`-\\|/{}~§●";
        private final short[] slots = new short[65536];
        private final android.graphics.Rect src = new android.graphics.Rect();
        private final android.graphics.RectF dst = new android.graphics.RectF();
        private final Paint glyphPaint = new Paint();
        private android.graphics.Bitmap atlasArgb, atlas;
        private Canvas atlasCanvas;
        private int cellW, cellH, used;

        private void buildAtlas() {
            cellW = (int) Math.ceil(grid.cw);
            cellH = (int) Math.ceil(grid.ch);
            // Old bitmaps are left to the GC, not recycled: the GPU thread may still draw the last frame with them.
            atlasArgb = android.graphics.Bitmap.createBitmap(ATLAS_COLS * cellW, (ATLAS_SLOTS / ATLAS_COLS) * cellH,
                    android.graphics.Bitmap.Config.ARGB_8888);
            atlasCanvas = new Canvas(atlasArgb);
            java.util.Arrays.fill(slots, (short) -1);
            used = 0;
            text.setColor(0xFFFFFFFF);
            for (int i = 0; i < PRESET.length(); i++) addGlyph(PRESET.charAt(i));
            for (char c = 33; c < 127; c++) addGlyph(c);
            atlas = atlasArgb.extractAlpha();
        }

        private void addGlyph(char c) {
            if (slots[c] >= 0 || used >= ATLAS_SLOTS) return;
            int s = used++;
            slots[c] = (short) s;
            float x = (s % ATLAS_COLS) * cellW, y = (s / ATLAS_COLS) * cellH;
            atlasCanvas.drawText(String.valueOf(c), x + cellW / 2f, y + baseline, text);
        }

        /** The atlas slot for a glyph, adding it (and refreshing the alpha copy) the first time it is seen. */
        private int slot(char c) {
            int s = slots[c];
            if (s >= 0 || atlas == null) return s;
            if (used >= ATLAS_SLOTS) return -1;
            text.setColor(0xFFFFFFFF);
            addGlyph(c);
            atlas = atlasArgb.extractAlpha();
            return slots[c];
        }

        private void stats(long sim, long raster, long draw, int cells) {
            long now = SystemClock.uptimeMillis();
            if (statsSince == 0) statsSince = now;
            simNs += sim;
            rasterNs += raster;
            drawNs += draw;
            statsCells += cells;
            statsFrames++;
            if (now - statsSince < STATS_MS) return;
            float secs = (now - statsSince) / 1000f;
            Log.d(TAG, String.format(java.util.Locale.ROOT,
                    "%s: %.1f fps, sim %.2f ms, raster %.2f ms, draw %.2f ms, %d cells per frame",
                    banner != null ? "fireworks" : keys.get(index), statsFrames / secs, simNs / 1e6 / statsFrames, rasterNs / 1e6 / statsFrames,
                    drawNs / 1e6 / statsFrames, statsCells / statsFrames));
            statsSince = now;
            simNs = rasterNs = drawNs = statsCells = 0;
            statsFrames = 0;
        }
    }
}

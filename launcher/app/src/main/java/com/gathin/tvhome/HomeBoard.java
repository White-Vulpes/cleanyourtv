package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * home_board (config.xml or a theme): to-dos, notes and reminders in three columns in the free space between the
 * clock and the dock, from an add-on such as Home Board, which reads them from the owner's phone app. The launcher
 * only reads what the add-on publishes and stays offline; the panel is view only, takes no focus and has no
 * background, so the wallpaper or the ASCII animals show through. Like game_day, naming the package is the opt-in.
 */
final class HomeBoard {
    static final String META = "com.gathin.tvhome.board";
    private static final String[] COLUMNS = {"kind", "text", "detail", "due_ms", "all_day", "recurring", "priority", "updated_ms"};

    /** One line on the board. */
    static final class Entry {
        final String kind, text, detail;
        final long dueMs;
        final boolean allDay, recurring;
        final int priority;

        Entry(String kind, String text, String detail, long dueMs, boolean allDay, boolean recurring, int priority) {
            this.kind = kind;
            this.text = text;
            this.detail = detail;
            this.dueMs = dueMs;
            this.allDay = allDay;
            this.recurring = recurring;
            this.priority = priority;
        }
    }

    private final Context ctx;
    private final Uri uri;
    private final Handler main;
    private final Runnable onChange;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ContentObserver observer;
    private boolean watching;
    private List<Entry> entries = Collections.emptyList();
    private String status = "";

    private HomeBoard(Context ctx, Uri uri, Handler main, Runnable onChange) {
        this.ctx = ctx;
        this.uri = uri;
        this.main = main;
        this.onChange = onChange;
        observer = new ContentObserver(main) {
            @Override
            public void onChange(boolean selfChange) {
                read();
            }
        };
    }

    /** Null when the key is empty, the package is not installed, or it names no provider of its own. */
    static HomeBoard create(Context ctx, String pkg, Handler main, Runnable onChange) {
        if (pkg == null || pkg.trim().isEmpty()) return null;
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg.trim(), PackageManager.GET_META_DATA);
            String authority = info.metaData == null ? null : info.metaData.getString(META);
            if (authority == null || authority.trim().isEmpty()) return null;
            // The provider must be the named package's own, not another app's that claims the authority.
            ProviderInfo provider = pm.resolveContentProvider(authority.trim(), 0);
            if (provider == null || !pkg.trim().equals(provider.packageName)) return null;
            Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority.trim())
                    .appendPath("items").build();
            return new HomeBoard(ctx, uri, main, onChange);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** Home is on screen: read now (which lets the add-on fetch if it is stale) and watch for changes. */
    void start() {
        if (!watching) {
            try {
                ctx.getContentResolver().registerContentObserver(uri, false, observer);
                watching = true;
            } catch (SecurityException e) {
                // The add-on went away; the panel keeps what it had.
            }
        }
        read();
    }

    void stop() {
        if (watching) ctx.getContentResolver().unregisterContentObserver(observer);
        watching = false;
    }

    void shutdown() {
        stop();
        io.shutdownNow();
    }

    /** One query off the main thread (the provider lives in the add-on's process), then onChange on the main thread. */
    void read() {
        try {
            io.execute(() -> {
                List<Entry> rows = new ArrayList<>();
                String st = "";
                try (Cursor c = ctx.getContentResolver().query(uri, COLUMNS, null, null, null)) {
                    while (c != null && c.moveToNext()) {
                        String kind = c.getString(0);
                        if ("status".equals(kind)) {
                            st = c.getString(1) == null ? "" : c.getString(1);
                            continue;
                        }
                        String text = c.getString(1);
                        if (kind == null || text == null || text.trim().isEmpty()) continue;
                        rows.add(new Entry(kind, text.trim(), c.getString(2) == null ? "" : c.getString(2).trim(),
                                c.getLong(3), c.getInt(4) != 0, c.getInt(5) != 0, c.getInt(6)));
                    }
                } catch (RuntimeException gone) {
                    return;   // uninstalled or crashed mid-query: keep what is on screen
                }
                String finalStatus = st;
                main.post(() -> {
                    entries = rows;
                    status = finalStatus;
                    onChange.run();
                });
            });
        } catch (RejectedExecutionException shutDown) {
            // Home is being destroyed.
        }
    }

    List<Entry> entries() {
        return entries;
    }

    /** "" when fine, "no token", or the add-on's last fetch error. */
    String status() {
        return status;
    }

    // ---- the panel ---------------------------------------------------------------------------------------------

    /**
     * Three transparent columns: To do, Notes, Reminders. One line per item, so a column holds {@code maxItems}
     * of them; items past that are left off (there is no "+N more" line). Built in code; never focusable.
     */
    static final class Panel extends LinearLayout {
        private static final int TIME = 0xCCFFD27A, LATE = 0xFFFF8A80, DIM = 0x99FFFFFF, EMPTY = 0x80FFFFFF;
        private final Column todo, notes, reminders;
        private final float dp;
        private final int maxItems;
        private final float textSp;

        /** Home: 6 items of 15 sp. The screensaver: 10 of 14 sp. */
        Panel(Context c) {
            this(c, 6, 15f);
        }

        Panel(Context c, int maxItems, float textSp) {
            super(c);
            this.maxItems = maxItems;
            this.textSp = textSp;
            dp = c.getResources().getDisplayMetrics().density;
            setOrientation(HORIZONTAL);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            todo = new Column(c, "To do");
            notes = new Column(c, "Notes");
            reminders = new Column(c, "Reminders");
            addView(todo, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
            addView(notes, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
            addView(reminders, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        }

        void show(List<Entry> all, String status) {
            List<Entry> t = new ArrayList<>(), n = new ArrayList<>(), r = new ArrayList<>();
            for (Entry e : all) {
                if ("birthday".equals(e.kind)) continue;   // Birthdays turn the background into fireworks instead
                if ("note".equals(e.kind)) n.add(e);
                else if ("reminder".equals(e.kind)) r.add(e);
                else t.add(e);
            }
            // Soonest first; reminders without a date go last, in the phone's order.
            r.sort((a, b) -> Long.compare(a.dueMs < 0 ? Long.MAX_VALUE : a.dueMs, b.dueMs < 0 ? Long.MAX_VALUE : b.dueMs));
            String hint = null;
            if (all.isEmpty() && !status.isEmpty()) {
                hint = "no token".equals(status) ? "Not connected to Todoist yet" : "Todoist: " + status;
            }
            List<CharSequence> lines = new ArrayList<>();
            for (Entry e : t) {
                lines.add(e.dueMs >= 0 ? join("○  " + e.text, "  · " + shortDay(e.dueMs), DIM) : "○  " + e.text);
            }
            todo.fill(lines, hint != null ? hint : "Nothing to do");
            lines = new ArrayList<>();
            for (Entry e : n) lines.add(e.detail.isEmpty() ? e.text : join(e.text, "  — " + e.detail, DIM));
            notes.fill(lines, hint != null ? "" : "No notes");
            lines = new ArrayList<>();
            for (Entry e : r) lines.add(reminder(e));
            reminders.fill(lines, hint != null ? "" : "No reminders");
        }

        /** "6:00 PM  Call mom": the time in gold (red once it has passed), then the text. */
        private static CharSequence reminder(Entry e) {
            boolean[] late = new boolean[1];
            String when = when(e, late);
            android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(when);
            b.setSpan(new android.text.style.ForegroundColorSpan(late[0] ? LATE : TIME), 0, when.length(), 0);
            b.append("  ").append(e.text);
            return b;
        }

        private static CharSequence join(String main, String tail, int tailColour) {
            android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(main).append(tail);
            b.setSpan(new android.text.style.ForegroundColorSpan(tailColour), main.length(), b.length(), 0);
            return b;
        }

        /** One column: a small heading, then one line per item, up to maxItems. */
        private final class Column extends LinearLayout {
            private final List<TextView> pool = new ArrayList<>();

            Column(Context c, String title) {
                super(c);
                setOrientation(VERTICAL);
                setPadding(0, 0, Math.round(28 * dp), 0);
                TextView head = text(c, 12, R.string.font_medium, 0xB3FFFFFF);
                head.setText(title);
                head.setAllCaps(true);
                head.setLetterSpacing(0.22f);
                head.setPadding(0, 0, 0, Math.round(8 * dp));
                addView(head);
            }

            void fill(List<CharSequence> lines, String empty) {
                int k = 0;
                if (lines.isEmpty()) {
                    if (!empty.isEmpty()) put(k++, empty, EMPTY);
                } else {
                    for (int i = 0; i < lines.size() && i < maxItems; i++) put(k++, lines.get(i), 0xEEFFFFFF);
                }
                for (int i = k; i < pool.size(); i++) pool.get(i).setVisibility(GONE);
            }

            private void put(int k, CharSequence s, int colour) {
                TextView v;
                if (k < pool.size()) {
                    v = pool.get(k);
                } else {
                    v = text(getContext(), textSp, R.string.font_regular, colour);
                    v.setSingleLine(true);
                    v.setEllipsize(TextUtils.TruncateAt.END);
                    v.setPadding(0, 0, 0, Math.round(4 * dp));
                    pool.add(v);
                    addView(v);
                }
                v.setVisibility(VISIBLE);
                v.setTextColor(colour);
                v.setText(s);
            }

            private TextView text(Context c, float sp, int font, int colour) {
                TextView v = new TextView(c);
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
                v.setTypeface(UiFont.get(c, font));
                v.setTextColor(colour);
                v.setIncludeFontPadding(false);
                v.setGravity(Gravity.START);
                v.setFocusable(false);
                // No background: a soft shadow keeps the words readable over a moving wallpaper.
                v.setShadowLayer(6 * dp, 0, dp, 0xCC000000);
                return v;
            }
        }

        /** "6:00 PM" today, "Tomorrow 9:00 AM", "Tue 25 Nov"; late[0] is set once it has passed. */
        private static String when(Entry e, boolean[] late) {
            if (e.dueMs < 0) return e.recurring ? "↻" : "•";
            ZoneId z = ZoneId.systemDefault();
            ZonedDateTime due = Instant.ofEpochMilli(e.dueMs).atZone(z);
            LocalDate today = LocalDate.now(z);
            late[0] = e.allDay ? due.toLocalDate().isBefore(today) : e.dueMs < System.currentTimeMillis();
            String time = e.allDay ? "" : due.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH));
            String day = due.toLocalDate().equals(today) ? "" : day(due.toLocalDate(), today);
            String s = day.isEmpty() ? (time.isEmpty() ? "Today" : time) : (time.isEmpty() ? day : day + " " + time);
            return s + (e.recurring ? " ↻" : "");
        }

        private static String shortDay(long ms) {
            ZoneId z = ZoneId.systemDefault();
            return day(Instant.ofEpochMilli(ms).atZone(z).toLocalDate(), LocalDate.now(z));
        }

        private static String day(LocalDate d, LocalDate today) {
            if (d.equals(today)) return "Today";
            if (d.equals(today.plusDays(1))) return "Tomorrow";
            if (d.equals(today.minusDays(1))) return "Yesterday";
            if (d.isAfter(today) && d.isBefore(today.plusDays(7))) {
                return d.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH));
            }
            return d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH));
        }
    }
}

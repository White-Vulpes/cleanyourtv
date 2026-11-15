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

    /** Three transparent columns: To do, Notes, Reminders. Built in code; never focusable. */
    static final class Panel extends LinearLayout {
        private static final int LINES = 6;
        private final Column todo, notes, reminders;
        private final float dp;

        Panel(Context c) {
            super(c);
            dp = c.getResources().getDisplayMetrics().density;
            setOrientation(HORIZONTAL);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setClipChildren(true);
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
            todo.fill(t, hint != null ? hint : "Nothing to do", LINES, Column.TODO);
            notes.fill(n, hint != null ? "" : "No notes", LINES, Column.NOTE);
            reminders.fill(r, hint != null ? "" : "No reminders", LINES, Column.REMINDER);
        }

        /** One column: a small heading and up to LINES lines, then "+N more". */
        private final class Column extends LinearLayout {
            static final int TODO = 0, NOTE = 1, REMINDER = 2;
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

            void fill(List<Entry> items, String empty, int lines, int type) {
                int used = 0, shown = 0;
                List<CharSequence[]> rows = new ArrayList<>();   // {small line or null, main line}
                for (Entry e : items) {
                    int cost = type == REMINDER || (type == NOTE && !e.detail.isEmpty()) ? 2 : 1;
                    boolean last = shown == items.size() - 1;
                    if (used + cost > lines - (last ? 0 : 1)) break;
                    if (type == REMINDER) {
                        rows.add(new CharSequence[] {when(e), e.text});
                    } else if (type == NOTE) {
                        rows.add(new CharSequence[] {null, e.text});
                        if (!e.detail.isEmpty()) rows.add(new CharSequence[] {"~", e.detail});
                    } else {
                        String due = e.dueMs >= 0 ? "  · " + shortDay(e.dueMs) : "";
                        rows.add(new CharSequence[] {null, "○  " + e.text + due});
                    }
                    used += cost;
                    shown++;
                }
                int rest = items.size() - shown;
                int k = 0;
                Context c = getContext();
                if (items.isEmpty()) {
                    if (!empty.isEmpty()) k = put(c, k, null, empty, 0x80FFFFFF);
                } else {
                    for (CharSequence[] row : rows) {
                        if ("~".equals(row[0])) {
                            k = put(c, k, null, row[1], 0x99FFFFFF);
                        } else {
                            if (row[0] != null) k = put(c, k, row[0], null, 0);
                            k = put(c, k, null, row[1], 0xEEFFFFFF);
                        }
                    }
                    if (rest > 0) k = put(c, k, null, "+" + rest + " more", 0x80FFFFFF);
                }
                for (int i = k; i < pool.size(); i++) pool.get(i).setVisibility(GONE);
            }

            /** Reuse or add the k-th line: a small kicker when kicker != null, else a body line. */
            private int put(Context c, int k, CharSequence kicker, CharSequence body, int colour) {
                TextView v;
                if (k < pool.size()) {
                    v = pool.get(k);
                } else {
                    v = text(c, 15, R.string.font_regular, 0xEEFFFFFF);
                    v.setSingleLine(true);
                    v.setEllipsize(TextUtils.TruncateAt.END);
                    pool.add(v);
                    addView(v);
                }
                v.setVisibility(VISIBLE);
                if (kicker != null) {
                    v.setText(kicker);
                    v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                    v.setTypeface(UiFont.get(c, R.string.font_medium));
                    v.setLetterSpacing(0.08f);
                    v.setTextColor(kicker.toString().startsWith("Overdue") ? 0xFFFF8A80 : 0xCCFFD27A);
                    v.setPadding(0, Math.round(2 * dp), 0, 0);
                } else {
                    v.setText(body);
                    v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    v.setTypeface(UiFont.get(c, R.string.font_regular));
                    v.setLetterSpacing(0f);
                    v.setTextColor(colour);
                    v.setPadding(0, 0, 0, Math.round(3 * dp));
                }
                return k + 1;
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

        private static String when(Entry e) {
            ZoneId z = ZoneId.systemDefault();
            ZonedDateTime due = Instant.ofEpochMilli(e.dueMs).atZone(z);
            if (e.dueMs < 0) return e.recurring ? "Repeats" : "No date";
            LocalDate today = LocalDate.now(z);
            long now = System.currentTimeMillis();
            String day = day(due.toLocalDate(), today);
            String time = e.allDay ? "" : " " + due.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH));
            boolean overdue = e.allDay ? due.toLocalDate().isBefore(today) : e.dueMs < now;
            return (overdue ? "Overdue · " : "") + day + time + (e.recurring ? "  ↻" : "");
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

package io.github.whitevulpes.homeboard;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fetches the board when Home asks for it and it is older than a minute. There is no background service and no
 * timer: Home queries the board when it comes on screen and once a minute while it stays there, so the TV only
 * talks to Todoist while someone is looking at Home.
 */
final class Sync {
    static final long STALE_MS = 60_000;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private Sync() {}

    /** Fetch in the background if the board is stale; tells observers of {@code changed} when it is done. */
    static void maybeRefresh(Context ctx, Uri changed) {
        Store store = new Store(ctx);
        if (store.token().isEmpty()) return;
        if (System.currentTimeMillis() - store.lastAttempt() < STALE_MS) return;
        if (!RUNNING.compareAndSet(false, true)) return;
        Context app = ctx.getApplicationContext();
        IO.execute(() -> {
            try {
                refreshNow(app);
            } finally {
                RUNNING.set(false);
                app.getContentResolver().notifyChange(changed, null);
            }
        });
    }

    /** Fetch now on this thread; returns "" or the error. */
    static String refreshNow(Context ctx) {
        Store store = new Store(ctx);
        long now = System.currentTimeMillis();
        store.markAttempt(now);
        String token = store.token();
        if (token.isEmpty()) {
            store.saveError("no Todoist token");
            return "no Todoist token";
        }
        String[] p = store.boardNames();
        try {
            List<Item> items = new Todoist(token).fetch(p[0], p[1], p[2], p[3], p[4]);
            store.saveBoard(items, now);
            return "";
        } catch (IOException e) {
            String msg = e.getMessage() == null ? "could not reach Todoist" : e.getMessage();
            store.saveError(msg);
            return msg;
        }
    }
}

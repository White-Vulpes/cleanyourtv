package io.github.whitevulpes.homeboard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * The setup script's door, guarded by WRITE_SECURE_SETTINGS, which only the ADB shell holds:
 * <pre>
 *   content call --uri content://io.github.whitevulpes.homeboard.setup --method token --arg TOKEN
 *   content call --uri ... --method board --arg "TV Board"                         the project
 *   content call --uri ... --method board --arg "TV Board|To do|Notes|Reminders"   and its section names
 *   content call --uri ... --method board --arg "TV Board|To do|Notes|Reminders|Birthdays"
 *   content call --uri ... --method refresh        fetch now; answers ok or the reason it failed
 *   content call --uri ... --method status
 * </pre>
 * The token is never returned, logged or shown.
 */
public final class SetupProvider extends ContentProvider {
    private static final Uri BOARD = Uri.parse("content://io.github.whitevulpes.homeboard.board/items");

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Store store = new Store(getContext());
        Bundle out = new Bundle();
        switch (method) {
            case "token":
                store.setToken(arg);
                out.putString("result", store.token().isEmpty() ? "token cleared" : "token saved");
                break;
            case "board": {
                String[] p = arg == null ? new String[0] : arg.split("\\|");
                boolean ok = p.length == 1 || p.length == 4 || p.length == 5;
                for (String x : p) ok &= !x.trim().isEmpty();
                if (!ok) {
                    out.putString("result", "error: give the project, or project|to do|notes|reminders");
                } else if (p.length == 1) {
                    store.setBoardNames(p[0].trim(), Store.DEFAULT_TODO, Store.DEFAULT_NOTES, Store.DEFAULT_REMINDERS);
                    out.putString("result", "project saved");
                } else {
                    store.setBoardNames(p[0].trim(), p[1].trim(), p[2].trim(), p[3].trim());
                    if (p.length == 5) store.setBirthdaysSection(p[4].trim());
                    out.putString("result", "project and sections saved");
                }
                break;
            }
            case "refresh": {
                String err = Sync.refreshNow(getContext());
                getContext().getContentResolver().notifyChange(BOARD, null);
                out.putString("result", err.isEmpty() ? "ok " + count(store) : "error: " + err);
                break;
            }
            case "status": {
                String[] p = store.boardNames();
                out.putString("result", (store.token().isEmpty() ? "no token" : "token set")
                        + "; project " + p[0] + ", sections " + p[1] + " | " + p[2] + " | " + p[3] + " | " + p[4]
                        + "; " + count(store)
                        + (store.error().isEmpty() ? "" : "; last error: " + store.error()));
                break;
            }
            default:
                out.putString("result", "error: unknown method " + method);
        }
        return out;
    }

    private static String count(Store store) {
        int todo = 0, notes = 0, reminders = 0, birthdays = 0;
        for (Item i : store.board()) {
            if (Item.TODO.equals(i.kind)) todo++;
            else if (Item.NOTE.equals(i.kind)) notes++;
            else if (Item.REMINDER.equals(i.kind)) reminders++;
            else birthdays++;
        }
        return todo + " to-dos, " + notes + " notes, " + reminders + " reminders, " + birthdays + " birthdays";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}

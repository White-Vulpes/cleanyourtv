package io.github.whitevulpes.homeboard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.List;

/**
 * What Home reads (content://io.github.whitevulpes.homeboard.board/items): one row per to-do, note and reminder.
 * Read only and holding no token. The first row is a status row (kind "status") so Home can say when Todoist is not
 * connected or the last fetch failed. Each query may start a fetch in the background when the board is stale.
 */
public final class BoardProvider extends ContentProvider {
    static final String[] COLUMNS = {"kind", "text", "detail", "due_ms", "all_day", "recurring", "priority", "updated_ms"};

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Store store = new Store(getContext());
        MatrixCursor c = new MatrixCursor(COLUMNS);
        String status = store.token().isEmpty() ? "no token" : store.error();
        c.addRow(new Object[] {"status", status, "", -1L, 0, 0, 0, store.lastOk()});
        List<Item> items = store.board();
        for (Item i : items) {
            c.addRow(new Object[] {i.kind, i.text, i.detail, i.dueMs, i.allDay ? 1 : 0, i.recurring ? 1 : 0,
                    i.priority, store.lastOk()});
        }
        c.setNotificationUri(getContext().getContentResolver(), uri);
        Sync.maybeRefresh(getContext(), uri);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/vnd.homeboard.item";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read only");
    }
}

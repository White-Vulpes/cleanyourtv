package io.github.whitevulpes.homeboard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The token, the project and section names and the last board fetched, all private to this app. The board is kept on
 * disk so Home shows it at once after a restart, before the first fetch finishes, and when the network is down.
 */
final class Store {
    static final String DEFAULT_PROJECT = "TV Board";
    static final String DEFAULT_TODO = "To do", DEFAULT_NOTES = "Notes", DEFAULT_REMINDERS = "Reminders";

    private final SharedPreferences prefs;
    private final File cache;

    Store(Context ctx) {
        prefs = ctx.getSharedPreferences("board", Context.MODE_PRIVATE);
        cache = new File(ctx.getFilesDir(), "board.json");
    }

    String token() {
        return prefs.getString("token", "");
    }

    void setToken(String token) {
        prefs.edit().putString("token", token == null ? "" : token.trim()).remove("ok").remove("error").apply();
        cache.delete();
    }

    /** {project, to-do section, notes section, reminders section}. */
    String[] boardNames() {
        return new String[] {prefs.getString("project", DEFAULT_PROJECT), prefs.getString("s_todo", DEFAULT_TODO),
                prefs.getString("s_notes", DEFAULT_NOTES), prefs.getString("s_reminders", DEFAULT_REMINDERS)};
    }

    void setBoardNames(String project, String todo, String notes, String reminders) {
        prefs.edit().putString("project", project).putString("s_todo", todo).putString("s_notes", notes)
                .putString("s_reminders", reminders).apply();
    }

    long lastAttempt() {
        return prefs.getLong("attempt", 0);
    }

    void markAttempt(long now) {
        prefs.edit().putLong("attempt", now).apply();
    }

    long lastOk() {
        return prefs.getLong("ok", 0);
    }

    /** A short reason the last fetch failed, or "" after a good one. Never holds the token. */
    String error() {
        return prefs.getString("error", "");
    }

    void saveError(String error) {
        prefs.edit().putString("error", error == null ? "error" : error).apply();
    }

    synchronized void saveBoard(List<Item> items, long now) throws IOException {
        JSONArray a = new JSONArray();
        try {
            for (Item i : items) a.put(i.toJson());
        } catch (JSONException e) {
            throw new IOException(e);
        }
        File tmp = new File(cache.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(a.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(cache)) throw new IOException("could not save the board");
        prefs.edit().putLong("ok", now).putString("error", "").apply();
    }

    synchronized List<Item> board() {
        List<Item> out = new ArrayList<>();
        if (!cache.exists()) return out;
        try {
            JSONArray a = new JSONArray(new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) out.add(Item.fromJson(a.getJSONObject(i)));
        } catch (IOException | JSONException e) {
            // A damaged cache is just an empty board until the next fetch.
        }
        return out;
    }
}

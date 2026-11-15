package io.github.whitevulpes.homeboard;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Reads the board from Todoist's API v1 (https://developer.todoist.com/api/v1/) with a personal token: one project
 * by name, its sections, then its active tasks. Completed tasks are not returned by the API, so ticking one off
 * on the phone takes it off the TV. Read only: nothing is ever written to Todoist.
 */
final class Todoist {
    private static final String BASE = "https://api.todoist.com/api/v1/";
    private static final int TIMEOUT_MS = 15_000;
    /** Safety cap on pages per list; at 200 a page that is far more than a board can show. */
    private static final int MAX_PAGES = 10;

    private final String token;

    Todoist(String token) {
        this.token = token;
    }

    /**
     * The board from one project whose sections are the columns: tasks in the to-do section (or in no section) are
     * to-dos, the notes section notes, the reminders section reminders; other sections are left out. An
     * IOException's message is short and safe to show (it never contains the token).
     */
    List<Item> fetch(String projectName, String todoSection, String notesSection, String remindersSection)
            throws IOException {
        String project = find(list("projects"), projectName);
        if (project == null) throw new IOException("no project named " + projectName);
        String pid = URLEncoder.encode(project, "UTF-8");
        String todo = null, notes = null, reminders = null;
        for (JSONObject sec : list("sections?project_id=" + pid)) {
            if (sec.optBoolean("is_deleted", false) || sec.optBoolean("is_archived", false)) continue;
            String name = norm(sec.optString("name", "")), id = sec.optString("id", null);
            if (name.equals(norm(todoSection))) todo = id;
            else if (name.equals(norm(notesSection))) notes = id;
            else if (name.equals(norm(remindersSection))) reminders = id;
        }
        List<Item> out = new ArrayList<>();
        for (JSONObject t : list("tasks?project_id=" + pid)) {
            String section = t.isNull("section_id") ? null : t.optString("section_id", null);
            if (section != null && section.isEmpty()) section = null;
            String kind;
            if (section == null || section.equals(todo)) kind = Item.TODO;
            else if (section.equals(notes)) kind = Item.NOTE;
            else if (section.equals(reminders)) kind = Item.REMINDER;
            else continue;
            if (t.optBoolean("checked", false) || t.optBoolean("is_deleted", false)) continue;
            // Sub-tasks stay on the phone; the board shows the top level.
            if (!t.isNull("parent_id") && !t.optString("parent_id", "").isEmpty()) continue;
            Item item = item(t, kind);
            if (item != null) out.add(item);
        }
        out.sort(Comparator.comparingInt(i -> i.order));
        return out;
    }

    private static Item item(JSONObject t, String kind) {
        String text = clean(t.optString("content", ""));
        if (text.isEmpty()) return null;
        String detail = clean(firstLine(t.optString("description", "")));
        long due = -1;
        boolean allDay = false, recurring = false;
        JSONObject d = t.optJSONObject("due");
        if (d != null) {
            recurring = d.optBoolean("is_recurring", false);
            String when = d.isNull("datetime") ? "" : d.optString("datetime", "");
            if (when.isEmpty()) when = d.optString("date", "");
            String zone = d.isNull("timezone") ? "" : d.optString("timezone", "");
            due = parseDue(when, zone);
            allDay = due >= 0 && when.length() == 10;
        }
        return new Item(kind, text, detail, due, allDay, recurring, t.optInt("priority", 1),
                t.optInt("child_order", t.optInt("order", 0)));
    }

    /** "2026-11-15" (all day), "2026-11-15T18:00:00" (floating, local time), with "Z" or an offset (fixed). */
    static long parseDue(String s, String zone) {
        if (s == null || s.isEmpty()) return -1;
        try {
            if (s.length() == 10) return LocalDate.parse(s).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            if (s.endsWith("Z")) return Instant.parse(s).toEpochMilli();
            if (s.length() > 19 && (s.charAt(19) == '+' || s.charAt(19) == '-' || s.charAt(19) == '.')) {
                try {
                    return OffsetDateTime.parse(s).toInstant().toEpochMilli();
                } catch (DateTimeParseException ignored) {
                    // fractional seconds without an offset: fall through to local time
                }
            }
            ZoneId z = ZoneId.systemDefault();
            if (zone != null && !zone.isEmpty()) {
                try {
                    z = ZoneId.of(zone);
                } catch (RuntimeException ignored) {
                    // an unknown zone name: the TV's own
                }
            }
            return LocalDateTime.parse(s).atZone(z).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return -1;
        }
    }

    /** Every item of a cursor-paginated list ({"results": [...], "next_cursor": ...}); a bare array also works. */
    private List<JSONObject> list(String path) throws IOException {
        List<JSONObject> out = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            String url = BASE + path + (path.contains("?") ? "&" : "?") + "limit=200"
                    + (cursor == null ? "" : "&cursor=" + URLEncoder.encode(cursor, "UTF-8"));
            String body = get(url);
            try {
                Object json = body.trim().startsWith("[") ? new JSONArray(body) : new JSONObject(body);
                JSONArray results = json instanceof JSONArray ? (JSONArray) json : ((JSONObject) json).optJSONArray("results");
                if (results == null) throw new IOException("unexpected answer from Todoist");
                for (int i = 0; i < results.length(); i++) {
                    JSONObject o = results.optJSONObject(i);
                    if (o != null) out.add(o);
                }
                cursor = json instanceof JSONObject && !((JSONObject) json).isNull("next_cursor")
                        ? ((JSONObject) json).optString("next_cursor", null) : null;
            } catch (JSONException e) {
                throw new IOException("unexpected answer from Todoist");
            }
            if (cursor == null || cursor.isEmpty()) break;
        }
        return out;
    }

    private String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code == 401 || code == 403) throw new IOException("Todoist refused the token");
            if (code == 410) throw new IOException("Todoist retired this API version: update Home Board");
            if (code == 429) throw new IOException("Todoist asked to slow down");
            if (code / 100 != 2) throw new IOException("Todoist answered " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) {
                    buf.write(b, 0, n);
                    if (buf.size() > 4 * 1024 * 1024) throw new IOException("answer from Todoist too large");
                }
                return buf.toString(StandardCharsets.UTF_8.name());
            }
        } catch (java.net.UnknownHostException e) {
            throw new IOException("no internet");
        } catch (java.net.SocketTimeoutException e) {
            throw new IOException("Todoist did not answer");
        } finally {
            c.disconnect();
        }
    }

    private static String find(List<JSONObject> projects, String name) {
        String want = norm(name);
        for (JSONObject p : projects) {
            if (p.optBoolean("is_deleted", false) || p.optBoolean("is_archived", false)) continue;
            if (norm(p.optString("name", "")).equals(want)) return p.optString("id", null);
        }
        return null;
    }

    /** "To do", "To-do" and "todo" are one name. */
    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    /** Todoist's own markdown, reduced to what reads on a TV: [text](link) to text, no ** or __, no leading "* ". */
    static String clean(String s) {
        String t = s.replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1").replace("**", "").replace("__", "").trim();
        if (t.startsWith("* ")) t = t.substring(2).trim();
        return t.length() > 140 ? t.substring(0, 139) + "…" : t;
    }
}

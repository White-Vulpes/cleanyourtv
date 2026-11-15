package io.github.whitevulpes.homeboard;

import org.json.JSONException;
import org.json.JSONObject;

/** One line on the board: a to-do, a note or a reminder, as read from a Todoist task. */
final class Item {
    static final String TODO = "todo", NOTE = "note", REMINDER = "reminder";

    final String kind;
    final String text;
    /** First line of the task's description, or "". */
    final String detail;
    /** When it is due, epoch ms; -1 for none. All-day dates are local midnight. */
    final long dueMs;
    final boolean allDay;
    final boolean recurring;
    /** Todoist priority: 1 normal to 4 urgent. */
    final int priority;
    /** Position in its project, as on the phone. */
    final int order;

    Item(String kind, String text, String detail, long dueMs, boolean allDay, boolean recurring, int priority, int order) {
        this.kind = kind;
        this.text = text;
        this.detail = detail;
        this.dueMs = dueMs;
        this.allDay = allDay;
        this.recurring = recurring;
        this.priority = priority;
        this.order = order;
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("kind", kind).put("text", text).put("detail", detail).put("due", dueMs)
                .put("allDay", allDay).put("recurring", recurring).put("priority", priority).put("order", order);
    }

    static Item fromJson(JSONObject o) {
        return new Item(o.optString("kind", TODO), o.optString("text", ""), o.optString("detail", ""),
                o.optLong("due", -1), o.optBoolean("allDay", false), o.optBoolean("recurring", false),
                o.optInt("priority", 1), o.optInt("order", 0));
    }
}

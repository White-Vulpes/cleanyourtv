package com.gathin.tvhome;

import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Birthdays: on someone's birthday the ASCII background (Home and the screensaver) turns into fireworks behind
 * "HAPPY BIRTHDAY <NAME>" for the whole day, then goes back to the animals by itself. Two sources, merged:
 * <ul>
 *   <li>config.xml or a theme, {@code birthdays}: "Mau=11-17" (month-day) or "Mau=2001-11-17";</li>
 *   <li>home_board: tasks in the Birthdays section of the Todoist project, the name as the task and the date as
 *       its due date (make it repeat every year). Only the month and day count.</li>
 * </ul>
 */
final class Birthdays {
    private Birthdays() {}

    /** The names whose birthday is today, in order, without repeats. */
    static List<String> today(List<String> config, List<HomeBoard.Entry> board) {
        ZoneId zone = ZoneId.systemDefault();
        MonthDay now = MonthDay.from(LocalDate.now(zone));
        Set<String> names = new LinkedHashSet<>();
        if (config != null) {
            for (String item : config) {
                int eq = item == null ? -1 : item.lastIndexOf('=');
                if (eq <= 0) continue;
                MonthDay day = parse(item.substring(eq + 1).trim());
                if (now.equals(day)) names.add(item.substring(0, eq).trim());
            }
        }
        if (board != null) {
            for (HomeBoard.Entry e : board) {
                if (!"birthday".equals(e.kind) || e.dueMs < 0) continue;
                MonthDay day = MonthDay.from(Instant.ofEpochMilli(e.dueMs).atZone(zone).toLocalDate());
                if (now.equals(day)) names.add(e.text.trim());
            }
        }
        names.remove("");
        return new ArrayList<>(names);
    }

    /** "HAPPY BIRTHDAY MAU", "HAPPY BIRTHDAY MAU & RIYA", or null when nobody's birthday is today. */
    static String banner(List<String> names) {
        if (names.isEmpty()) return null;
        return "Happy Birthday " + String.join(" & ", names);
    }

    /** "11-17", "--11-17" or "2001-11-17"; null when it is none of those. */
    static MonthDay parse(String s) {
        try {
            if (s.length() == 10) return MonthDay.from(LocalDate.parse(s));
            if (s.length() == 5) return MonthDay.parse("--" + s);
            if (s.length() == 7 && s.startsWith("--")) return MonthDay.parse(s);
        } catch (RuntimeException e) {
            // not a date
        }
        return null;
    }
}

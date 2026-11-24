package dev.blockfolk.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Readable durations for countdowns and typed intervals. */
public final class Durations {

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*(min|d|h|m)?");

    private Durations() {
    }

    /**
     * Minutes as {@code 45 min}, {@code 1 h 30 min}, or {@code 2 d 3 h 5 min};
     * {@code 0} is {@code never}.
     */
    public static String minutes(int minutes) {
        if (minutes <= 0)
            return "never";
        int days = minutes / (24 * 60);
        int hours = minutes / 60 % 24;
        int rest = minutes % 60;
        StringBuilder text = new StringBuilder();
        if (days > 0)
            text.append(days).append(" d");
        if (hours > 0)
            text.append(text.isEmpty() ? "" : " ").append(hours).append(" h");
        if (rest > 0)
            text.append(text.isEmpty() ? "" : " ").append(rest).append(" min");
        return text.toString();
    }

    /**
     * Time left until a countdown ends, rounded up to whole minutes, such as
     * {@code 42 min}; under a minute it is {@code under a minute}.
     */
    public static String remaining(long millis) {
        if (millis < 60_000L)
            return "under a minute";
        return minutes((int) Math.min(Integer.MAX_VALUE, (millis + 59_999L) / 60_000L));
    }

    /**
     * Reads {@code 90}, {@code 90m}, {@code 1h30m}, {@code 2h}, or {@code 1d} as
     * minutes. A bare number counts as minutes. Returns -1 when the text is not a
     * duration.
     */
    public static int parseMinutes(String text) {
        if (text == null)
            return -1;
        String value = text.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (value.isEmpty())
            return -1;
        Matcher matcher = PART.matcher(value);
        long total = 0;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end)
                return -1;
            end = matcher.end();
            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException exception) {
                return -1;
            }
            String unit = matcher.group(2);
            total += unit == null || unit.startsWith("m") ? amount : unit.equals("h") ? amount * 60 : amount * 1440;
            if (total > Integer.MAX_VALUE)
                return -1;
        }
        return end == value.length() ? (int) total : -1;
    }
}

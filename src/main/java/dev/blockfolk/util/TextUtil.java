package dev.blockfolk.util;

public final class TextUtil {

    private TextUtil() { }

    public static String stripCodeFence(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.startsWith("```")) return trimmed;
        int newline = trimmed.indexOf('\n');
        int end = trimmed.lastIndexOf("```");
        return newline >= 0 && end > newline ? trimmed.substring(newline + 1, end).trim() : trimmed;
    }

    public static String abbreviate(String value, int maximumLength) {
        if (maximumLength < 4) throw new IllegalArgumentException("Maximum length must be at least four");
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength - 3) + "...";
    }

    public static String abbreviateSingleLine(String value, int maximumLength) {
        return abbreviate(value.replace('\n', ' '), maximumLength);
    }
}

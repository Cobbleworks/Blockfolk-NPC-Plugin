package dev.blockfolk.util;

import java.util.ArrayList;
import java.util.List;

public final class TextUtil {

    private TextUtil() {
    }

    public static String stripCodeFence(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.startsWith("```"))
            return trimmed;
        int newline = trimmed.indexOf('\n');
        int end = trimmed.lastIndexOf("```");
        return newline >= 0 && end > newline ? trimmed.substring(newline + 1, end).trim() : trimmed;
    }

    public static String abbreviate(String value, int maximumLength) {
        if (maximumLength < 4)
            throw new IllegalArgumentException("Maximum length must be at least four");
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength - 3) + "...";
    }

    public static String abbreviateSingleLine(String value, int maximumLength) {
        return abbreviate(value.replace('\n', ' '), maximumLength);
    }

    public static List<String> wrap(String value, int maximumLength) {
        if (maximumLength < 1)
            throw new IllegalArgumentException("Maximum length must be positive");
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : value.trim().split("\\s+")) {
            if (word.isEmpty())
                continue;
            if (!line.isEmpty() && line.length() + 1 + word.length() > maximumLength) {
                lines.add(line.toString());
                line.setLength(0);
            }
            int offset = 0;
            while (word.length() - offset > maximumLength) {
                lines.add(word.substring(offset, offset + maximumLength));
                offset += maximumLength;
            }
            if (offset < word.length()) {
                if (!line.isEmpty())
                    line.append(' ');
                line.append(word, offset, word.length());
            }
        }
        if (!line.isEmpty())
            lines.add(line.toString());
        return lines;
    }
}

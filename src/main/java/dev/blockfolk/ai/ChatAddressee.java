package dev.blockfolk.ai;

import java.util.List;
import java.util.Locale;

/** Finds a named NPC in a chat line, falling back to the nearest candidate. */
final class ChatAddressee {

    private ChatAddressee() {
    }

    static int select(String message, List<String> names) {
        if (names.isEmpty())
            return -1;
        String line = message == null ? "" : message.toLowerCase(Locale.ROOT);
        int selected = 0;
        int longest = 0;
        for (int index = 0; index < names.size(); index++) {
            String name = NpcResponseIds.plainName(names.get(index)).trim().toLowerCase(Locale.ROOT);
            if (name.length() <= longest || name.isEmpty())
                continue;
            int start = line.indexOf(name);
            while (start >= 0) {
                int end = start + name.length();
                boolean left = start == 0 || !wordCharacter(line.charAt(start - 1));
                boolean right = end == line.length() || !wordCharacter(line.charAt(end));
                if (left && right) {
                    selected = index;
                    longest = name.length();
                    break;
                }
                start = line.indexOf(name, start + 1);
            }
        }
        return selected;
    }

    private static boolean wordCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }
}

package dev.blockfolk.model;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public enum TargetCategory {
    MOBS, ANIMALS, PLAYERS, NPCS;

    public static Set<TargetCategory> fromFlags(boolean mobs, boolean animals, boolean players, boolean npcs) {
        EnumSet<TargetCategory> categories = EnumSet.noneOf(TargetCategory.class);
        if (mobs)
            categories.add(MOBS);
        if (animals)
            categories.add(ANIMALS);
        if (players)
            categories.add(PLAYERS);
        if (npcs)
            categories.add(NPCS);
        return categories;
    }

    public static Set<TargetCategory> fromStored(Collection<String> stored) {
        EnumSet<TargetCategory> categories = EnumSet.noneOf(TargetCategory.class);
        for (TargetCategory category : values()) {
            if (stored.stream().anyMatch(value -> category.name().equalsIgnoreCase(value.trim())))
                categories.add(category);
        }
        return categories;
    }

    public static List<String> storedValues(Set<TargetCategory> categories) {
        return categories.stream().sorted().map(category -> category.name().toLowerCase(Locale.ROOT)).toList();
    }
}

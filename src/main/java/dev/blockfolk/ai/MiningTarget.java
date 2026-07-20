package dev.blockfolk.ai;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Material;

/** Validated, model-facing resource names for the MINE_BLOCKS action. */
public final class MiningTarget {

    private static final Set<String> TARGETS = Set.of(
            "any", "ores", "resources", "coal", "gold", "iron", "copper", "diamond",
            "emerald", "redstone", "lapis", "quartz", "ancient_debris", "obsidian", "stone");

    private MiningTarget() { }

    public static boolean valid(String value) {
        if (value == null || value.isBlank()) return true;
        String[] values = value.split(",");
        return values.length <= 8 && Arrays.stream(values)
                .map(MiningTarget::normalize).allMatch(TARGETS::contains);
    }

    public static boolean matches(Material material, String value) {
        String requested = value == null || value.isBlank() ? "ores" : value;
        return Arrays.stream(requested.split(","))
                .map(MiningTarget::normalize)
                .anyMatch(target -> matchesOne(material, target));
    }

    private static boolean matchesOne(Material material, String target) {
        String name = material.name().toLowerCase(Locale.ROOT);
        if (target.equals("any")) return true;
        if (target.equals("ores") || target.equals("resources")) {
            return name.endsWith("_ore") || name.equals("ancient_debris");
        }
        if (target.equals("ancient_debris")) return name.equals(target);
        if (target.equals("obsidian")) return name.equals("obsidian") || name.equals("crying_obsidian");
        if (target.equals("stone")) {
            return name.equals("stone") || name.equals("deepslate") || name.equals("netherrack")
                    || name.equals("tuff") || name.equals("calcite");
        }
        if (target.equals("quartz")) return name.equals("nether_quartz_ore");
        return name.endsWith("_ore") && name.contains(target);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}

package dev.blockfolk.ai;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.bukkit.Material;

/** Shared validated targets for AI and deterministic GATHER_BLOCKS actions. */
public final class MiningTarget {

    public static final int MAX_SELECTIONS = 8;
    private static final List<String> TARGETS = List.of(
            "resources", "ores", "wood", "stone", "any", "coal", "gold", "iron", "copper", "diamond",
            "emerald", "redstone", "lapis", "quartz", "ancient_debris", "obsidian", "logs",
            "oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
            "mangrove", "cherry", "pale_oak", "crimson", "warped", "bamboo");

    private MiningTarget() { }

    public static boolean valid(String value) {
        if (value == null || value.isBlank()) return true;
        String[] values = value.split(",");
        return values.length <= MAX_SELECTIONS && Arrays.stream(values)
                .map(MiningTarget::normalize).allMatch(TARGETS::contains);
    }

    public static List<String> targets() {
        return TARGETS.stream().distinct().toList();
    }

    public static String modelTargetList() {
        return String.join(", ", targets());
    }

    public static List<String> selection(String value) {
        if (value == null || value.isBlank()) return List.of("resources");
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        Arrays.stream(value.split(",")).map(MiningTarget::normalize)
                .filter(TARGETS::contains).limit(MAX_SELECTIONS).forEach(selected::add);
        return selected.isEmpty() ? List.of("resources") : List.copyOf(selected);
    }

    public static String storedSelection(Iterable<String> values) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = normalize(value);
            if (TARGETS.contains(normalized)) selected.add(normalized);
            if (selected.size() == MAX_SELECTIONS) break;
        }
        return selected.isEmpty() ? "resources" : String.join(",", selected);
    }

    public static String displaySelection(String value) {
        return selection(value).stream().map(MiningTarget::displayName)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    public static String displayName(String value) {
        String normalized = normalize(value);
        return Arrays.stream(normalized.split("_"))
                .map(part -> part.isEmpty() ? part
                        : Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    public static boolean matches(Material material, String value) {
        String requested = value == null || value.isBlank() ? "resources" : value;
        return Arrays.stream(requested.split(","))
                .map(MiningTarget::normalize)
                .anyMatch(target -> matchesOne(material, target));
    }

    public static boolean isWood(Material material) {
        return isWood(material.name().toLowerCase(Locale.ROOT));
    }

    private static boolean matchesOne(Material material, String target) {
        String name = material.name().toLowerCase(Locale.ROOT);
        if (target.equals("any")) return isResource(name) || isStone(name);
        if (target.equals("ores")) return isOre(name);
        if (target.equals("resources")) return isResource(name);
        if (target.equals("ancient_debris")) return name.equals(target);
        if (target.equals("obsidian")) return name.equals("obsidian") || name.equals("crying_obsidian");
        if (target.equals("stone")) return isStone(name);
        if (target.equals("wood") || target.equals("logs")) return isWood(name);
        if (isWood(name)) {
            String unstripped = name.startsWith("stripped_") ? name.substring("stripped_".length()) : name;
            return unstripped.startsWith(target + "_");
        }
        if (target.equals("quartz")) return name.equals("nether_quartz_ore");
        return name.endsWith("_ore") && name.contains(target);
    }

    private static boolean isResource(String name) {
        return isOre(name) || isWood(name) || name.equals("obsidian") || name.equals("crying_obsidian");
    }

    private static boolean isOre(String name) {
        return name.endsWith("_ore") || name.equals("ancient_debris");
    }

    private static boolean isWood(String name) {
        return name.endsWith("_log") || name.endsWith("_wood") || name.endsWith("_stem")
                || name.endsWith("_hyphae") || name.equals("bamboo_block")
                || name.equals("stripped_bamboo_block");
    }

    private static boolean isStone(String name) {
        return name.equals("stone") || name.equals("deepslate") || name.equals("netherrack")
                || name.equals("tuff") || name.equals("calcite");
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}

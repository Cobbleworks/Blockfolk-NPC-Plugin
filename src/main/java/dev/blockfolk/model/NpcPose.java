package dev.blockfolk.model;

import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.entity.Pose;

/**
 * Player-shaped poses supported by mannequins, plus the mounted sitting pose.
 */
public enum NpcPose {
    STANDING("Standing", Pose.STANDING, Material.ARMOR_STAND), CROUCHING("Crouching", Pose.SNEAKING,
            Material.LEATHER_BOOTS), SLEEPING("Sleeping", Pose.SLEEPING, Material.RED_BED), SITTING("Sitting",
                    Pose.STANDING, Material.OAK_STAIRS), SWIMMING("Swimming / Crawling", Pose.SWIMMING,
                            Material.WATER_BUCKET), FALL_FLYING("Fall Flying", Pose.FALL_FLYING, Material.ELYTRA);

    private final String displayName;
    private final Pose nativePose;
    private final Material material;

    NpcPose(String displayName, Pose nativePose, Material material) {
        this.displayName = displayName;
        this.nativePose = nativePose;
        this.material = material;
    }

    public String displayName() {
        return displayName;
    }
    public Pose nativePose() {
        return nativePose;
    }
    public Material material() {
        return material;
    }

    public static NpcPose fromStored(String value) {
        if (value == null)
            return STANDING;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return STANDING;
        }
    }
}

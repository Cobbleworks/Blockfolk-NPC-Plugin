package dev.blockfolk.fighters;

import org.bukkit.Material;
import org.bukkit.Particle;
import dev.blockfolk.fighters.FighterAttack.Visual;

/** Visual themes shared by the editor, impact, and caster indicators. */
public final class AbilityVisuals {
    private AbilityVisuals() {
    }

    public static Particle particle(Visual visual) {
        return switch (visual) {
            case FLAME -> Particle.FLAME;
            case SONIC -> Particle.SONIC_BOOM;
            case SOUL -> Particle.SOUL;
            case ICE -> Particle.SNOWFLAKE;
            case POISON -> Particle.WITCH;
            case CLOUD -> Particle.CLOUD;
            case BLOOD -> Particle.DAMAGE_INDICATOR;
            case LIGHTNING -> Particle.ELECTRIC_SPARK;
            case ENDER -> Particle.PORTAL;
            case ENCHANT -> Particle.ENCHANT;
            case HEARTS -> Particle.HEART;
            case SMOKE -> Particle.SMOKE;
            case SOUL_FLAME -> Particle.SOUL_FIRE_FLAME;
            case BUBBLES -> Particle.BUBBLE_POP;
            case SPORES -> Particle.SPORE_BLOSSOM_AIR;
            case TOTEM -> Particle.TOTEM_OF_UNDYING;
        };
    }

    public static Material icon(Visual visual) {
        return switch (visual) {
            case FLAME -> Material.BLAZE_POWDER;
            case SONIC -> Material.SCULK_SHRIEKER;
            case ICE -> Material.PACKED_ICE;
            case POISON -> Material.SPIDER_EYE;
            case CLOUD -> Material.FEATHER;
            case BLOOD -> Material.REDSTONE;
            case LIGHTNING -> Material.LIGHTNING_ROD;
            case ENDER -> Material.ENDER_PEARL;
            case SOUL -> Material.SOUL_LANTERN;
            case ENCHANT -> Material.ENCHANTED_BOOK;
            case HEARTS -> Material.POPPY;
            case SMOKE -> Material.CAMPFIRE;
            case SOUL_FLAME -> Material.SOUL_TORCH;
            case BUBBLES -> Material.WATER_BUCKET;
            case SPORES -> Material.SPORE_BLOSSOM;
            case TOTEM -> Material.TOTEM_OF_UNDYING;
        };
    }

    public static String description(Visual visual) {
        return switch (visual) {
            case FLAME -> "Orange flames trace the spell's area";
            case SONIC -> "Warden sonic waves and sonic boom sound";
            case SOUL -> "Drifting souls trace a spectral spell";
            case ICE -> "Snowflakes outline a frosty spell";
            case POISON -> "Purple witch particles mark a cursed area";
            case CLOUD -> "White cloud puffs mark the spell's shape";
            case BLOOD -> "Red damage particles create a blood effect";
            case LIGHTNING -> "Electric sparks; spheres add visual lightning";
            case ENDER -> "Purple portal particles create an Ender effect";
            case ENCHANT -> "Enchantment glyphs create an arcane effect";
            case HEARTS -> "Floating hearts create a life magic effect";
            case SMOKE -> "Smoke wisps outline a dark spell";
            case SOUL_FLAME -> "Blue soul flames create ghostly fire";
            case BUBBLES -> "Popping bubbles create a water magic effect";
            case SPORES -> "Drifting blossom spores create nature magic";
            case TOTEM -> "Green and gold sparks create a totem effect";
        };
    }
}

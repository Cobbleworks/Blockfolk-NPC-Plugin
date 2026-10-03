package dev.blockfolk.fighters;

import org.bukkit.Material;
import org.bukkit.Particle;
import dev.blockfolk.fighters.FighterAttack.Visual;

/** Visual themes shared by the editor, impact, and caster indicators. */
public final class AbilityVisuals {
    /** Every telegraphed cast uses the same warning so players learn to react to it. */
    public static final String CAST_SOUND = "minecraft:entity.evoker.prepare_attack";

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
            case HOLY -> Particle.END_ROD;
            case SCULK -> Particle.SCULK_SOUL;
            case CHERRY -> Particle.CHERRY_LEAVES;
            case WIND -> Particle.SMALL_GUST;
            case GLOW -> Particle.GLOW;
        };
    }

    /**
     * Particle used while charging or warning. Sonic booms are too large to
     * outline a shape, so they show sparks until impact.
     */
    public static Particle warningParticle(Visual visual) {
        return visual == Visual.SONIC ? Particle.ELECTRIC_SPARK : particle(visual);
    }

    /** Impact sound matching the theme. */
    public static String impactSound(Visual visual) {
        return "minecraft:" + switch (visual) {
            case FLAME -> "entity.blaze.shoot";
            case SONIC -> "entity.warden.sonic_boom";
            case SOUL -> "particle.soul_escape";
            case ICE -> "block.glass.break";
            case POISON -> "entity.witch.throw";
            case CLOUD -> "entity.phantom.flap";
            case BLOOD -> "entity.player.attack.crit";
            case LIGHTNING -> "entity.lightning_bolt.impact";
            case ENDER -> "entity.enderman.teleport";
            case ENCHANT, SMOKE -> "entity.evoker.cast_spell";
            case HEARTS -> "block.amethyst_block.chime";
            case SOUL_FLAME -> "item.firecharge.use";
            case BUBBLES -> "block.bubble_column.bubble_pop";
            case SPORES -> "block.spore_blossom.break";
            case TOTEM -> "item.totem.use";
            case HOLY -> "block.beacon.activate";
            case SCULK -> "block.sculk_catalyst.bloom";
            case CHERRY -> "block.cherry_leaves.break";
            case WIND -> "entity.breeze.wind_burst";
            case GLOW -> "entity.glow_squid.squirt";
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
            case HOLY -> Material.BEACON;
            case SCULK -> Material.SCULK_CATALYST;
            case CHERRY -> Material.CHERRY_LEAVES;
            case WIND -> Material.WIND_CHARGE;
            case GLOW -> Material.GLOW_INK_SAC;
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
            case HOLY -> "White light rods create a radiant, holy effect";
            case SCULK -> "Sculk souls rise for a deep-dark effect";
            case CHERRY -> "Falling cherry petals create a graceful effect";
            case WIND -> "Small gusts create a breeze-like wind effect";
            case GLOW -> "Glowing motes create a bioluminescent effect";
        };
    }
}

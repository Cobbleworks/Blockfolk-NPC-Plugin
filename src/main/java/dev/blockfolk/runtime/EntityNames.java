package dev.blockfolk.runtime;

import java.util.Locale;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import dev.blockfolk.ai.NpcResponseIds;
import dev.blockfolk.repository.NpcDefinitionRepository;

/**
 * Names entities for AI event memory: Blockfolk NPCs and players by name, named
 * mobs by their name, other mobs by kind ("a zombie").
 */
final class EntityNames {

    private EntityNames() {
    }

    static String describe(Entity entity, NpcInstanceRegistry instances, NpcDefinitionRepository definitions) {
        if (entity == null)
            return null;
        var npc = instances.findByEntityId(entity.getEntityId());
        if (npc.isPresent()) {
            return definitions.find(npc.get().getDefinitionKey())
                    .map(definition -> "the NPC " + NpcResponseIds.plainName(definition.getDisplayName()))
                    .orElse("an NPC");
        }
        if (entity instanceof Player player)
            return "the player " + player.getName();
        if (entity.customName() != null)
            return entity.getName();
        return withArticle(entity.getType().name());
    }

    /** "IRON_GOLEM" becomes "an iron golem". */
    static String withArticle(String constantName) {
        String noun = constantName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return ("aeiou".indexOf(noun.isEmpty() ? 'x' : noun.charAt(0)) >= 0 ? "an " : "a ") + noun;
    }
}

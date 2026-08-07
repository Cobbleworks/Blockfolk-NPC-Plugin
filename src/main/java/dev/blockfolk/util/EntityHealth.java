package dev.blockfolk.util;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;

public final class EntityHealth {

    private EntityHealth() {
    }

    public static double maximum(LivingEntity entity) {
        var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? entity.getHealth() : attribute.getValue();
    }
}

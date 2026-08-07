package dev.blockfolk.gui;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.util.SkinTextureUtil;

public final class NpcHeadUtil {

    private NpcHeadUtil() {
    }

    public static ItemStack applySkin(ItemStack head, NpcDefinition definition) {
        if (!(head.getItemMeta() instanceof SkullMeta meta)) {
            return head;
        }
        String texture = definition.getSkinTextureValue();
        if (texture == null) {
            texture = SkinTextureUtil.toTextureProperty(definition.getSkinUrl());
        }
        if (texture == null) {
            return head;
        }
        try {
            UUID uuid = UUID.nameUUIDFromBytes(definition.getKey().getBytes(StandardCharsets.UTF_8));
            PlayerProfile profile = Bukkit.createProfileExact(uuid, "Blockfolk");
            String signature = definition.getSkinTextureSignature();
            profile.setProperty(signature == null
                    ? new ProfileProperty("textures", texture)
                    : new ProfileProperty("textures", texture, signature));
            meta.setPlayerProfile(profile);
            head.setItemMeta(meta);
        } catch (RuntimeException ignored) {
            // A broken skin value must never prevent a management GUI opening.
        }
        return head;
    }
}

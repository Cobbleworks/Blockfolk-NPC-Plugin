package dev.blockfolk.gui;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import com.destroystokyo.paper.profile.ProfileProperty;

import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.util.SkinTextureUtil;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;

public final class NpcHeadUtil {

    private NpcHeadUtil() {
    }

    public static ItemStack applySkin(ItemStack head, NpcDefinition definition) {
        if (!(head.getItemMeta() instanceof SkullMeta)) {
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
            ResolvableProfile.Builder profile = ResolvableProfile.resolvableProfile().uuid(uuid).name("Blockfolk");
            String signature = definition.getSkinTextureSignature();
            profile.addProperty(signature == null
                    ? new ProfileProperty("textures", texture)
                    : new ProfileProperty("textures", texture, signature));
            head.setData(DataComponentTypes.PROFILE, profile);
        } catch (RuntimeException ignored) {
            // A broken skin value must never prevent a management GUI opening.
        }
        return head;
    }
}

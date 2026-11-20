package dev.blockfolk.gui;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import com.destroystokyo.paper.profile.ProfileProperty;

import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.util.SkinTextureUtil;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;

public final class NpcHeadUtil {

    private NpcHeadUtil() {
    }

    public static ItemStack applySkin(ItemStack head, NpcDefinition definition) {
        return applySkin(head, definition.getKey(), definition.getSkinUrl(), definition.getSkinTextureValue(),
                definition.getSkinTextureSignature());
    }

    /**
     * Shows the skin a variant actually renders with: its own, else the preset's.
     */
    public static ItemStack applySkin(ItemStack head, NpcInstance instance, NpcDefinition definition) {
        return applySkin(head, instance.getId().toString(), instance.resolvedSkinUrl(definition),
                instance.resolvedSkinTextureValue(definition), instance.resolvedSkinTextureSignature(definition));
    }

    private static ItemStack applySkin(ItemStack head, String seed, String skinUrl, String textureValue,
            String signature) {
        if (!(head.getItemMeta() instanceof SkullMeta)) {
            return head;
        }
        String texture = textureValue;
        if (texture == null) {
            texture = SkinTextureUtil.toTextureProperty(skinUrl);
        }
        if (texture == null) {
            return head;
        }
        try {
            UUID uuid = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
            ResolvableProfile.Builder profile = ResolvableProfile.resolvableProfile().uuid(uuid).name("Blockfolk");
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

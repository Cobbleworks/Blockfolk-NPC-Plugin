package dev.blockfolk.runtime;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;

import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.ShopOffer;
import dev.blockfolk.model.ShopProfile;
import dev.blockfolk.util.UiText;
import net.kyori.adventure.text.Component;

/**
 * Opens an NPC preset's shop in the vanilla trading screen. Stock is unlimited.
 */
public final class NpcShopService {

    /**
     * Opens the shop of {@code definition} for {@code player}. Admins are told why
     * nothing opened.
     *
     * @param preview
     *            when true the shop opens even if it is disabled, so admins can
     *            test their trades
     * @return whether the trading screen was opened
     */
    public boolean open(Player player, NpcDefinition definition, boolean preview) {
        ShopProfile shop = definition.getShopProfile();
        if (!shop.enabled() && !preview) {
            notifyAdmin(player, "The shop of " + definition.getDisplayName() + " is disabled.");
            return false;
        }
        if (shop.validOffers().isEmpty()) {
            notifyAdmin(player, "The shop of " + definition.getDisplayName() + " has no complete trades.");
            return false;
        }
        Merchant merchant = Bukkit.getServer().createMerchant();
        merchant.setRecipes(shop.validOffers().stream().map(NpcShopService::recipe).toList());
        Component title = Component.text(Objects.requireNonNullElse(shop.title(), definition.getDisplayName()));
        MenuType.MERCHANT.builder().merchant(merchant).title(title).checkReachable(false).build(player).open();
        return true;
    }

    private static MerchantRecipe recipe(ShopOffer offer) {
        // A fresh merchant is built for every opening, so the use limit never locks a
        // trade.
        MerchantRecipe recipe = new MerchantRecipe(offer.result(), 0, Integer.MAX_VALUE, false, 0, 0f, true);
        recipe.addIngredient(offer.cost());
        if (offer.secondCost() != null)
            recipe.addIngredient(offer.secondCost());
        return recipe;
    }

    private static void notifyAdmin(Player player, String message) {
        if (player.hasPermission("blockfolk.admin"))
            player.sendMessage(UiText.warning(message));
    }
}

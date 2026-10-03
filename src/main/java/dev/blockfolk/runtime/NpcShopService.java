package dev.blockfolk.runtime;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;

import dev.blockfolk.model.Shop;
import dev.blockfolk.model.ShopOffer;
import dev.blockfolk.repository.ShopRepository;
import dev.blockfolk.util.UiText;
import net.kyori.adventure.text.Component;

/**
 * Opens a shared shop in the vanilla trading screen. Stock is unlimited.
 */
public final class NpcShopService {
    private final ShopRepository shops;

    public NpcShopService(ShopRepository shops) {
        this.shops = shops;
    }

    /**
     * Opens the shop stored under {@code shopKey} for {@code player}. Admins are
     * told why nothing opened.
     *
     * @return whether the trading screen was opened
     */
    public boolean open(Player player, String shopKey) {
        if (shopKey == null) {
            notifyAdmin(player, "This Open Shop action has no shop selected.");
            return false;
        }
        Shop shop = shops.find(shopKey).orElse(null);
        if (shop == null) {
            notifyAdmin(player, "The shop '" + shopKey + "' no longer exists.");
            return false;
        }
        return open(player, shop);
    }

    public boolean open(Player player, Shop shop) {
        if (shop.validOffers().isEmpty()) {
            notifyAdmin(player, "The shop " + shop.name() + " has no complete trades.");
            return false;
        }
        Merchant merchant = Bukkit.getServer().createMerchant();
        merchant.setRecipes(shop.validOffers().stream().map(NpcShopService::recipe).toList());
        MenuType.MERCHANT.builder().merchant(merchant).title(Component.text(shop.name())).checkReachable(false)
                .build(player).open();
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

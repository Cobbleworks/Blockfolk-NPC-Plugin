package dev.blockfolk.runtime;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import net.milkbowl.vault.economy.Economy;

/**
 * The economy registered with Vault. The provider is looked up on every use, so
 * an economy plugin that enables after Blockfolk, or is replaced at runtime, is
 * picked up without a restart. Only load this class when Vault is installed.
 */
public final class VaultShopEconomy implements ShopEconomy {

    private Economy economy() {
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        Economy economy = registration == null ? null : registration.getProvider();
        return economy != null && economy.isEnabled() ? economy : null;
    }

    @Override
    public boolean available() {
        return economy() != null;
    }

    @Override
    public String providerName() {
        Economy economy = economy();
        return economy == null ? "none (Vault found, but no economy plugin)" : economy.getName();
    }

    @Override
    public String format(double amount) {
        Economy economy = economy();
        return economy == null ? ShopEconomy.plain(amount) : economy.format(amount);
    }

    @Override
    public double balance(OfflinePlayer player) {
        Economy economy = economy();
        return economy == null ? 0 : economy.getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        Economy economy = economy();
        return economy != null && economy.has(player, amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        Economy economy = economy();
        return economy != null && economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        Economy economy = economy();
        return economy != null && economy.depositPlayer(player, amount).transactionSuccess();
    }
}

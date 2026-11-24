package dev.blockfolk.runtime;

import java.util.Locale;

import org.bukkit.OfflinePlayer;

/**
 * Money for shop trades. Blockfolk talks to the server's economy plugin through
 * Vault; without one, {@link #none()} keeps item trades working and reports
 * money trades as unavailable.
 */
public interface ShopEconomy {

    /** Whether an economy plugin is connected right now. */
    boolean available();

    /** The economy plugin's name, or a short explanation of why there is none. */
    String providerName();

    /** The amount as the economy plugin writes it, such as {@code 1,250.00 €}. */
    String format(double amount);

    double balance(OfflinePlayer player);

    boolean has(OfflinePlayer player, double amount);

    /** Takes money; false when the economy refused. */
    boolean withdraw(OfflinePlayer player, double amount);

    /** Gives money; false when the economy refused. */
    boolean deposit(OfflinePlayer player, double amount);

    /** No economy: money trades are unavailable. */
    static ShopEconomy none() {
        return new ShopEconomy() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public String providerName() {
                return "none (install Vault and an economy plugin)";
            }

            @Override
            public String format(double amount) {
                return plain(amount);
            }

            @Override
            public double balance(OfflinePlayer player) {
                return 0;
            }

            @Override
            public boolean has(OfflinePlayer player, double amount) {
                return false;
            }

            @Override
            public boolean withdraw(OfflinePlayer player, double amount) {
                return false;
            }

            @Override
            public boolean deposit(OfflinePlayer player, double amount) {
                return false;
            }
        };
    }

    /** {@code 12}, {@code 12.5}, or {@code 12.25}: no trailing zero decimals. */
    static String plain(double amount) {
        java.math.BigDecimal value = java.math.BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return value.scale() <= 0 ? value.setScale(0).toPlainString() : value.toPlainString();
    }

    /**
     * Reads a typed amount such as {@code 12}, {@code 12.50}, or {@code 12,50}.
     * Returns -1 when the text is not a non-negative number.
     */
    static double parse(String text) {
        if (text == null)
            return -1;
        String trimmed = text.trim().replace(" ", "").replace("_", "");
        if (trimmed.indexOf(',') >= 0 && trimmed.indexOf('.') < 0 && trimmed.indexOf(',') == trimmed.lastIndexOf(',')
                && trimmed.length() - trimmed.indexOf(',') <= 3)
            trimmed = trimmed.replace(',', '.');
        else
            trimmed = trimmed.replace(",", "");
        if (!trimmed.matches("\\d+(\\.\\d+)?"))
            return -1;
        try {
            return Double.parseDouble(trimmed.toLowerCase(Locale.ROOT));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }
}

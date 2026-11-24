# Vault integration

Vault is an optional soft dependency. With Vault and an economy plugin installed, shop trades can cost money, pay money out, or both. See [Shops](../features/shop.md#money).

## Setup

1. Install [Vault](https://www.spigotmc.org/resources/vault.34315/) and an economy plugin that registers with it, such as EssentialsX.
2. Restart the server.
3. In a shop's trade editor, click a trade's pane and set a **Price** or **Payout**.

No additional option in `config.yml` is required. The trade settings show whether an economy is connected and which plugin provides it.

Blockfolk uses whatever economy Vault provides and asks it to format amounts, so prices appear with the server's currency, such as `120 €` or `$120.00`. An economy plugin that starts after Blockfolk, or is replaced while the server runs, is picked up without a restart.

## Without an economy

Item-for-item trades never need Vault. Without an economy, trades with a price or payout stay in the shop but show as unavailable to players. Administrators who open such a shop are told that no economy is connected.

## How payments happen

A trade first checks the player's items and inventory space, then takes the price, then swaps the items, then pays out. If the economy refuses the payout, the items go back and the price is refunded, so a trade never half happens. Should the refund also be refused, Blockfolk logs the amount and the player so you can refund it by hand.

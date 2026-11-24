# Shops

Shops are shared: build a shop once in the shop library and open it from as many NPCs as you like. Changing a shop's trades updates it for every NPC that uses it. Setting up shops requires `blockfolk.admin`; players need no permission to trade.

A trade can be paid with items, with money, or both, and can give items, money, or both. A shop can show a **rotating assortment** that changes on a timer, and trades can have a **stock limit** that refills when the shop restocks.

## Set up a shop

1. Run `/bf shops` or `/bf shop` to open the shop library, then click **Create Shop** and enter a name. You can also run `/bf shop create <name>`.
2. Each row of the trade editor holds two trades. A trade has three item slots: **cost**, an optional **second cost**, and the **result**. Place the items you want, with the amounts you want, directly into the slots.
3. Click the pane between a trade's costs and its result to open its **trade settings**: a money price or payout, a stock limit, and whether the trade is offered. See [Trade settings](#trade-settings).
4. Optionally click **Assortment & Restock** to let the shop rotate its trades and restock on a timer. See [Rotating assortment](#rotating-assortment).
5. Optionally click **Shop Name** to rename the shop. The name is the shop screen's title.
6. Click **Preview Shop** to see the shop exactly as players will see it. Trades made in the preview are real.
7. In the NPC editor, open **Event Behaviour → On Right-Click** (or any other trigger), add the **Open Shop** action, and choose the shop.

Players can now right-click the NPC to trade. The trade editor saves when you close it, change page, or click any button.

Each page holds ten trades. Once a page is full, **Next Page** adds another, up to five pages (50 trades). Empty trades are removed when saved.

### What the panes mean

| Pane | Meaning |
| --- | --- |
| Gray | Empty trade. |
| Red | Incomplete: the player must give something (an item or a price) and get something (an item or a payout). Players never see it. |
| Green | Offered. In a rotating shop it is drawn into the assortment. |
| Light blue | Always Offered: on sale in every assortment. |
| Light gray | Not Offered: kept in the editor but never sold. |

The pane's tooltip lists the trade's price, payout, stock, and draw weight.

## Custom items

Items trade exactly as placed. Everything about an item is kept, including its custom name, lore, enchantments (also levels beyond vanilla), and other item data. This works in both directions:

- **Selling a custom item:** place it in the result slot, for example a renamed diamond sword with Sharpness V. Players receive exactly that item.
- **Buying a custom item from players:** place it as a cost. Players must offer an exactly matching item: a plain diamond sword does not count as the renamed, enchanted one, and a damaged tool does not match an undamaged one.

## Trade settings

Click a trade's pane in the trade editor to open its settings. Changes save immediately; **Back** returns to the editor page you came from. The settings stay with the trade while you change its items.

| Setting | Description |
| --- | --- |
| **Price** | Money the player pays per trade, on top of any cost items. 0 = no money price. |
| **Payout** | Money the player receives per trade, on top of any result item. Leave the result empty to buy items from players, for example 16 wheat for 5 coins. 0 = no payout. |
| **Availability** | **Offered** (the default) sells the trade; in a rotating shop it is drawn into the assortment. **Always Offered** sells it in every assortment. **Not Offered** keeps it in the editor without selling it, for example for seasonal items. |
| **Draw Weight** | 1 to 100, default 10. How likely the trade is to be drawn when the shop rotates. A trade with weight 20 comes up about twice as often as one with weight 10. The tooltip shows its share of the pool. |
| **Stock** | How often the trade can be made until the shop restocks. 0 = unlimited. |
| **Stock Applies** | **Shared**: one stock for all players, so whoever buys first gets it. **Per Player**: every player can make the trade up to the limit. |

Numbers change with left-click (down) and right-click (up); shift-click changes them five times as much. Middle-click types an exact value, such as `12.50` for a price.

The settings also show whether an economy is connected and how often the trade was sold since the last restock.

## Money

Money trades need [Vault](https://www.spigotmc.org/resources/vault.34315/) and an economy plugin that registers with it, such as EssentialsX. Blockfolk uses whatever economy Vault provides and writes amounts the way that economy formats them. An economy plugin that starts after Blockfolk is picked up without a restart.

Without an economy, item-for-item trades work as before. Trades with a price or payout show as unavailable to players, and administrators who open the shop are told why.

## Rotating assortment

Click **Assortment & Restock** in the trade editor.

| Setting | Description |
| --- | --- |
| **Rotating Trades** | How many **Offered** trades are drawn at random for each stock period. 0 turns rotation off: every offered trade is on sale. **Always Offered** trades are added on top. |
| **Restock Every** | How often the shop restocks, for example every hour. A restock refills every limited trade and, in a rotating shop, draws a new set of trades. 0 = only when restocked by hand. Typed values accept `90`, `90m`, `1h30m`, or `2d`; at most 30 days. |
| **Restock Now** | Restocks and draws a new rotation immediately. The next automatic restock follows one interval later. |

**Example:** a shop has 30 Offered trades, Rotating Trades set to 10, and Restock Every set to 1 h. Players see 10 random trades. An hour later they see 10 others: each new assortment skips the trades of the one before it whenever enough other trades are left. With 15 trades and 10 rotating, the 5 trades that were not on sale come first and 5 of the previous ones fill the rest.

Restocks follow real time and keep their schedule, also while the server is off. With an hourly restock that started at 14:20, the next restocks are at 15:20, 16:20, and so on. After a restart at 17:05, the shop shows the assortment for the 16:20 period. Every NPC that opens the shop shows the same assortment and shares its stock.

Editing a rotating shop does not wait for the next restock: a removed trade, or one switched to Not Offered, is replaced by another drawn trade right away. Raising Rotating Trades draws more trades, and lowering it removes some.

## What players see

Shops with only unlimited item-for-item trades open in the **vanilla villager trading screen**, as before. If the shop restocks on a timer, the title shows when the next restock is due.

Shops with a money price, a payout, or a stock limit on any offered trade open in the **shop menu** instead. Every trade shows its real item with its own name, enchantments, and lore, followed by:

- the price, the cost items, and the payout,
- the remaining stock (*Sold out until the next restock* when none is left),
- red lines for anything the player is missing, such as *3 × Emerald (you have 1)*.

| Click | Effect |
| --- | --- |
| Left-click | Make the trade once. |
| Shift-click | Repeat the trade up to a full stack of the result (sales: up to 64 times), stopping at the first trade that cannot happen. |

The bottom bar shows the player's balance (for shops with money trades) and the time until the next restock. Open shop screens update when the shop restocks.

A trade either happens completely or not at all. Items are checked first, and the result must fit into the player's inventory before any money moves. If the economy refuses a payout, the player gets their items and money back. Trades only use the player's hotbar and backpack, never worn armor or the off-hand.

## The shop library

The library lists every shop with its key, number of offered trades, rotation, restock interval, which screen it uses, and how many NPCs or routes use it.

| Click | Effect |
| --- | --- |
| Left-click | Edit trades |
| Right-click | Preview |
| Shift-left-click | Duplicate, for example to make a variant with different prices |
| Shift-right-click | Delete, after confirming. The confirmation lists the NPCs and routes whose Open Shop actions will stop working. |

The trade editor's **Used By** item lists the NPCs and routes that open the shop. The **Shops** button in the NPC editor shows which shops that NPC opens and leads to the library.

## Opening a shop

Shops only open through the **Open Shop** behaviour action, so you decide when players can trade. When you add the action, you choose a shop from the library. You can also create one from that menu, and right-click a shop there to edit its trades. You can, for example:

- add it to **On Right-Click** for a classic shopkeeper
- add it to an **Ask Question** answer such as "Show me your wares", with a different shop per answer
- combine it with **Send Dialog** for a greeting first
- add it to a route waypoint action

A shop without an Open Shop action is invisible to players, which lets you prepare trades before opening. To close a shop for one NPC, remove that NPC's Open Shop action.

Open Shop needs a player as its trigger, so it does nothing on triggers without one, such as Spawn or Idle. If the chosen shop has no offered trades or was deleted, nothing opens; administrators receive a warning explaining why.

## Upgrading

Shops from earlier versions keep working unchanged: their trades become Offered trades with unlimited stock, no money, and no rotation, and they still open in the vanilla trading screen. Each trade gets a permanent id on the first start, which `shops.yml` stores from then on.

Development builds stored one shop inside each NPC preset. On startup, each such shop moves into the library under the preset's key (keeping its title as the shop name), and that preset's Open Shop actions, including those on routes it owns, are pointed at it.

## Commands

| Command | Description |
| --- | --- |
| `/bf shop` | Open the shop library. |
| `/bf shops` | Open the shop library. |
| `/bf shop create <name>` | Create a shop and open its trade editor. |
| `/bf shop <shop>` | Open a shop's trade editor. |
| `/bf shop <shop> rename <name>` | Rename a shop; the name is the shop screen's title. |
| `/bf shop <shop> preview` | Open the shop for yourself as players see it. |
| `/bf shop <shop> restock` | Restock the shop and draw a new rotation now. |

# Shops

Shops are shared: build a shop once in the shop library and open it from as many NPCs as you like. Changing a shop's trades updates it for every NPC that uses it. Setting up shops requires `blockfolk.admin`; players need no permission to trade.

A trade can be paid with items, with money, or both, and can give items, money, or both. A shop can sell **one page per day** for an offer that changes with every Minecraft day, and trades can have a **stock limit** that refills at every sunrise.

## Set up a shop

1. Run `/bf shops` or `/bf shop` to open the shop library, then click **Create Shop** and enter a name. You can also run `/bf shop create <name>`.
2. Each row of the trade editor holds two trades. A trade has three item slots: **cost**, an optional **second cost**, and the **result**. Place the items you want, with the amounts you want, directly into the slots.
3. Click the pane between a trade's costs and its result to open its **trade settings**: a money price or payout and a stock limit. See [Trade settings](#trade-settings).
4. Optionally left-click **Pages** to switch the shop to **One Page per Day**, so each editor page becomes one day's offer. See [Daily pages](#daily-pages).
5. Optionally click **Shop Name** to rename the shop. The name is the shop screen's title.
6. Click **Preview Shop** to see the shop exactly as players will see it. Trades made in the preview are real.
7. In the NPC editor, open **Event Behaviour → On Right-Click** (or any other trigger), add the **Open Shop** action, and choose the shop.

Players can now right-click the NPC to trade. The trade editor saves when you close it, change page, or click any button.

Each page holds ten trades, and a shop has up to five pages (50 trades). Trades stay on the page and row where you put them, so clearing a trade never moves trades from a later page forward. The editor title shows the page number, and **(today)** marks the page on sale today in a one-page-per-day shop. **Previous Page** and **Next Page** show how many trades each page holds.

### What the panes mean

| Pane | Meaning |
| --- | --- |
| Gray | Empty trade. |
| Red | Incomplete: the player must give something (an item or a price) and get something (an item or a payout). Players never see it. |
| Green | Ready: sold whenever its page is on sale. |

The pane's tooltip lists the trade's price, payout, and stock.

To keep trades without selling them, for example seasonal items, put them in a shop that no NPC opens.

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
| **Stock** | How often the trade can be made per Minecraft day. It refills at sunrise. 0 = unlimited. |
| **Stock Applies** | **Shared**: one stock for all players, so whoever buys first gets it. **Per Player**: every player can make the trade up to the limit. |

Numbers change with left-click (down) and right-click (up); shift-click changes them five times as much. Middle-click types an exact value, such as `12.50` for a price.

The settings also show whether an economy is connected, which page the trade is on, and how often it was sold since the last restock.

## Money

Money trades need [Vault](https://www.spigotmc.org/resources/vault.34315/) and an economy plugin that registers with it, such as EssentialsX. Blockfolk uses whatever economy Vault provides and writes amounts the way that economy formats them. An economy plugin that starts after Blockfolk is picked up without a restart.

Without an economy, item-for-item trades work as before. Trades with a price or payout show as unavailable to players, and administrators who open the shop are told why.

## Daily pages

The **Pages** button in the trade editor decides which pages are on sale:

| Mode | What players see |
| --- | --- |
| **Show All Pages** (default) | Every trade on every page. |
| **One Page per Day** | One page per Minecraft day, in order: page 1, then page 2, and so on, back to page 1 after the last. Pages without trades are skipped. |

Left-click **Pages** to switch between the two. Its tooltip lists the pages with trades, the page on sale today, and the time until the next sunrise.

**Example:** a market stall has fish on page 1, bread and cake on page 2, and tools on page 3. With **One Page per Day**, players find fish on one day, bread and cake on the next, tools on the third day, and fish again on the fourth. Trades you want on sale every day, such as bread, go on every page.

### Restocking

A new Minecraft day starts at sunrise. Then every limited trade refills, in both modes, and a one-page-per-day shop turns to its next page. Sleeping through the night counts as a new day. Open shop screens update at sunrise.

- Shops follow the main overworld's clock, wherever their NPCs stand.
- If nobody opens the shop for a few days, it skips ahead one page per day that passed, so the schedule stays the same.
- Setting the time back, for example with `/time set day`, also counts as a new day and turns one page.
- While time stands still (the `advance_time` game rule is off), no new days come. Restock by hand instead.

Right-click **Pages**, or run `/bf shop <shop> restock`, to restock now. A one-page-per-day shop also turns to its next page. The next automatic restock still comes at sunrise.

Every NPC that opens the shop shows the same page and shares its stock.

## What players see

Shops with only unlimited item-for-item trades open in the **vanilla villager trading screen**, as before. The title shows when the next restock is due at sunrise.

Shops with a money price, a payout, or a stock limit on any trade open in the **shop menu** instead. Every trade shows its real item with its own name, enchantments, and lore, followed by:

- the price, the cost items, and the payout,
- the remaining stock (*Sold out until the next restock* when none is left),
- red lines for anything the player is missing, such as *3 × Emerald (you have 1)*.

| Click | Effect |
| --- | --- |
| Left-click | Make the trade once. |
| Shift-click | Repeat the trade up to a full stack of the result (sales: up to 64 times), stopping at the first trade that cannot happen. |

The bottom bar shows the player's balance (for shops with money trades) and the time until the next restock at sunrise. Open shop screens update when the shop restocks.

A trade either happens completely or not at all. Items are checked first, and the result must fit into the player's inventory before any money moves. If the economy refuses a payout, the player gets their items and money back. Trades only use the player's hotbar and backpack, never worn armor or the off-hand.

## The shop library

The library lists every shop with its key, number of trades, page mode, which screen it uses, and how many NPCs or routes use it.

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

Open Shop needs a player as its trigger, so it does nothing on triggers without one, such as Spawn or Idle. If the chosen shop has no complete trades or was deleted, nothing opens; administrators receive a warning explaining why.

## Upgrading

Shops from earlier versions keep working unchanged: their trades keep their order and page, have unlimited stock and no money, all pages are on sale, and they still open in the vanilla trading screen. Each trade gets a permanent id and slot on the first start, which `shops.yml` stores from then on.

Development builds with rotating assortments are converted too: a shop that rotated becomes a **One Page per Day** shop, and its **Not Offered** trades move into a new shop named "*name* (stored)", which no NPC opens. Draw weights and restock intervals are dropped.

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
| `/bf shop <shop> restock` | Restock the shop now; a one-page-per-day shop also turns to its next page. |

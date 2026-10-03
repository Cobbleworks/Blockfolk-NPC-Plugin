# Shops

Any NPC preset can run a small shop that uses the vanilla villager trading screen. Configure it from the NPC editor under **Shop**, or use `/bf shop <name>`. Setting up shops requires `blockfolk.admin`; players need no permission to trade.

## Set up a shop

1. Open the NPC editor and click **Shop**, or run `/bf shop <name>`.
2. Each row holds two trades. A trade has three item slots: **cost**, an optional **second cost**, and the **result**. Place the items you want, with the amounts you want, directly into the slots. The pane between the costs and the result turns green when the trade is ready and red when it is incomplete.
3. Click **Shop: Off** to switch the shop on.
4. Optionally click **Shop Title** to name the trading screen. By default it shows the NPC's name.
5. Click **Preview Shop** to see the trading screen exactly as players will see it.
6. Open **Event Behaviour → On Right-Click** (or any other trigger) and add the **Open Shop** action.

Players can now right-click the NPC to trade. The shop is saved when you close the editor, change page, or click any button.

Each page holds ten trades. Once a page is full, **Next Page** adds another, up to five pages (50 trades). Incomplete trades stay in the editor but are hidden from players until they have a cost and a result. Empty trades are removed when saved.

## How trading works

- Trades have **unlimited stock**: they never lock or need restocking, and prices never change.
- Trades are item-for-item, so any item can act as currency, including named or enchanted items. Players must offer an item that matches the cost item.
- Trades give no experience.
- The shop is defined per preset. Every instance of the preset sells the same trades, and duplicating a preset copies its shop.

## Opening the shop

Shops only open through the **Open Shop** behaviour action, so you decide when players can trade. For example, you can:

- add it to **On Right-Click** for a classic shopkeeper
- add it to an **Ask Question** answer such as "Show me your wares"
- combine it with **Send Dialog** for a greeting first

Open Shop needs a player as its trigger, so it does nothing on triggers without one, such as Spawn or Idle. If the shop is disabled or has no complete trades, nothing opens; administrators receive a warning explaining why.

## Commands

| Command | Description |
| --- | --- |
| `/bf shop <name>` | Open the shop editor. |
| `/bf shop <name> <on\|off>` | Enable or disable the shop. |
| `/bf shop <name> title <text\|reset>` | Set the trading screen title; `reset` uses the NPC name. |
| `/bf shop <name> preview` | Open the trading screen for yourself, even while the shop is disabled. |

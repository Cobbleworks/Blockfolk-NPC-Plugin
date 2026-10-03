# Shops

Shops use the vanilla villager trading screen. They are shared: build a shop once in the shop library and open it from as many NPCs as you like. Changing a shop's trades updates it for every NPC that uses it. Setting up shops requires `blockfolk.admin`; players need no permission to trade.

## Set up a shop

1. Run `/bf shop` or click **Shops** in the main menu to open the shop library, then click **Create Shop** and enter a name. You can also run `/bf shop create <name>`.
2. Each row of the trade editor holds two trades. A trade has three item slots: **cost**, an optional **second cost**, and the **result**. Place the items you want, with the amounts you want, directly into the slots. The pane between the costs and the result turns green when the trade is ready and red when it is incomplete.
3. Optionally click **Shop Name** to rename the shop. The name is the trading screen title.
4. Click **Preview Shop** to see the trading screen exactly as players will see it.
5. In the NPC editor, open **Event Behaviour → On Right-Click** (or any other trigger), add the **Open Shop** action, and choose the shop.

Players can now right-click the NPC to trade. The trade editor saves when you close it, change page, or click any button.

Each page holds ten trades. Once a page is full, **Next Page** adds another, up to five pages (50 trades). Incomplete trades stay in the editor but are hidden from players until they have a cost and a result. Empty trades are removed when saved.

## The shop library

The library lists every shop with its key, number of complete trades, and how many NPCs or routes use it.

| Click | Effect |
| --- | --- |
| Left-click | Edit trades |
| Right-click | Preview |
| Shift-left-click | Duplicate, for example to make a variant with different prices |
| Shift-right-click | Delete, after confirming. The confirmation lists the NPCs and routes whose Open Shop actions will stop working. |

The trade editor's **Used By** item lists the NPCs and routes that open the shop. The **Shops** button in the NPC editor shows which shops that NPC opens and leads to the library.

## How trading works

- Trades have **unlimited stock**: they never lock or need restocking, and prices never change.
- Trades are item-for-item, so any item can act as currency, including named or enchanted items. Players must offer an item that matches the cost item.
- Trades give no experience.

## Opening a shop

Shops only open through the **Open Shop** behaviour action, so you decide when players can trade. When you add the action, you choose a shop from the library. You can also create one from that menu, and right-click a shop there to edit its trades. You can, for example:

- add it to **On Right-Click** for a classic shopkeeper
- add it to an **Ask Question** answer such as "Show me your wares", with a different shop per answer
- combine it with **Send Dialog** for a greeting first
- add it to a route waypoint action

A shop without an Open Shop action is invisible to players, which lets you prepare trades before opening. To close a shop for one NPC, remove that NPC's Open Shop action.

Open Shop needs a player as its trigger, so it does nothing on triggers without one, such as Spawn or Idle. If the chosen shop has no complete trades or was deleted, nothing opens; administrators receive a warning explaining why.

## Upgrading from per-NPC shops

Development builds stored one shop inside each NPC preset. On startup, each such shop moves into the library under the preset's key (keeping its title as the shop name), and that preset's Open Shop actions, including those on routes it owns, are pointed at it.

## Commands

| Command | Description |
| --- | --- |
| `/bf shop` | Open the shop library. |
| `/bf shop create <name>` | Create a shop and open its trade editor. |
| `/bf shop <shop>` | Open a shop's trade editor. |
| `/bf shop <shop> rename <name>` | Rename a shop; the name is the trading screen title. |
| `/bf shop <shop> preview` | Open the trading screen for yourself. |

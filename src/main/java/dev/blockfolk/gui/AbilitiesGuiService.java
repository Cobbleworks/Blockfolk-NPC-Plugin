package dev.blockfolk.gui;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.UnaryOperator;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import dev.blockfolk.combat.NpcSpecialAttackService;
import dev.blockfolk.fighters.FighterAttack;
import dev.blockfolk.fighters.AbilityVisuals;
import dev.blockfolk.fighters.FighterAttack.*;
import dev.blockfolk.fighters.FighterTemplates;
import dev.blockfolk.input.ChatInputService;
import dev.blockfolk.model.SpecialAttackOptions;
import dev.blockfolk.repository.FighterAttackRepository;
import dev.blockfolk.util.LegacyText;
import dev.blockfolk.util.UiText;

/** Shared ability library, template editor, and per-NPC assignment menus. */
public final class AbilitiesGuiService implements Listener {
    private static final int PAGE_SIZE = 45;
    private final FighterAttackRepository repository;
    private final ChatInputService input;
    private final Consumer<Player> mainMenu;
    private enum Tab {
        OVERVIEW, GEOMETRY, TIMING, EFFECTS, VISUALS
    }
    private interface Holder extends InventoryHolder {
        @Override
        default Inventory getInventory() {
            return null;
        }
    }
    private record LibraryHolder(int page, Consumer<Player> back) implements Holder {
    }
    private record SelectHolder(int page, Consumer<String> select, Consumer<Player> back) implements Holder {
    }
    private record TemplateHolder(Consumer<Player> back) implements Holder {
    }
    private record EditHolder(String key, Tab tab, Consumer<Player> back) implements Holder {
    }
    private record DeleteHolder(String key, Consumer<Player> back) implements Holder {
    }
    private record AssignHolder(SpecialAttackOptions options, Consumer<SpecialAttackOptions> save,
            Consumer<Player> back, int page) implements Holder {
    }

    public AbilitiesGuiService(FighterAttackRepository repository, ChatInputService input, Consumer<Player> mainMenu) {
        this.repository = repository;
        this.input = input;
        this.mainMenu = mainMenu;
    }
    public void open(Player player) {
        library(player, 0, mainMenu);
    }
    public void openAssignments(Player player, SpecialAttackOptions options, Consumer<SpecialAttackOptions> save,
            Consumer<Player> back) {
        assignments(player, new AssignHolder(options, save, back, 0));
    }

    public void selectAbility(Player player, Consumer<String> select, Consumer<Player> back) {
        selector(player, new SelectHolder(0, select, back));
    }
    public String abilityName(String key) {
        return repository.find(key).map(FighterAttack::name).orElse(key + " (missing)");
    }

    private void selector(Player player, SelectHolder holder) {
        List<FighterAttack> attacks = repository.findAll();
        int page = page(holder.page(), attacks.size());
        Inventory inventory = menu(new SelectHolder(page, holder.select(), holder.back()),
                "Use Ability · Choose Ability");
        for (int i = page * PAGE_SIZE; i < Math.min(attacks.size(), (page + 1) * PAGE_SIZE); i++) {
            FighterAttack attack = attacks.get(i);
            List<String> lore = summary(attack);
            lore.add(LegacyText.YELLOW + "Click: " + LegacyText.GRAY + "Use this ability in the action");
            inventory.setItem(i % PAGE_SIZE, abilityItem(attack, attack.name(), lore));
        }
        if (attacks.isEmpty())
            inventory.setItem(22,
                    item(Material.BARRIER, "No Abilities", List.of("Create an ability in /bf abilities first")));
        footer(inventory, page, attacks.size());
        show(player, inventory);
    }

    private void library(Player player, int requestedPage, Consumer<Player> back) {
        List<FighterAttack> attacks = repository.findAll();
        int page = page(requestedPage, attacks.size());
        Inventory inventory = menu(new LibraryHolder(page, back), "Abilities · Ability Library");
        for (int i = page * PAGE_SIZE; i < Math.min(attacks.size(), (page + 1) * PAGE_SIZE); i++) {
            FighterAttack attack = attacks.get(i);
            List<String> lore = summary(attack);
            lore.add(LegacyText.YELLOW + "Click: " + LegacyText.GRAY + "Edit this shared ability");
            lore.addAll(iconHints());
            inventory.setItem(i % PAGE_SIZE, abilityItem(attack, attack.name(), lore));
        }
        inventory.setItem(51,
                item(Material.EMERALD, "Create Ability", List.of("Choose a starting template, then enter a name")));
        footer(inventory, page, attacks.size());
        show(player, inventory);
    }
    private void templates(Player player, Consumer<Player> back) {
        Inventory inventory = menu(new TemplateHolder(back), "Abilities · Choose Template");
        List<FighterAttack> templates = FighterTemplates.defaults();
        for (int i = 0; i < templates.size(); i++) {
            FighterAttack attack = templates.get(i);
            inventory.setItem(i, abilityItem(attack, attack.name(), summary(attack)));
        }
        inventory.setItem(49, item(Material.BARRIER, "Back", List.of()));
        show(player, inventory);
    }
    private void assignments(Player player, AssignHolder holder) {
        List<String> keys = assignmentKeys(holder.options());
        int page = page(holder.page(), keys.size());
        holder = new AssignHolder(holder.options(), holder.save(), holder.back(), page);
        Inventory inventory = menu(holder, "Abilities · Assigned Abilities");
        for (int i = page * PAGE_SIZE; i < Math.min(keys.size(), (page + 1) * PAGE_SIZE); i++) {
            String key = keys.get(i);
            FighterAttack attack = repository.find(key).orElse(null);
            boolean enabled = holder.options().assignedAttackKeys().contains(key);
            List<String> lore = attack == null
                    ? new ArrayList<>(List.of(LegacyText.RED + "Definition was deleted; click to unassign"))
                    : summary(attack);
            lore.add(0, enabled ? LegacyText.GREEN + "Assigned" : LegacyText.RED + "Not assigned");
            if (attack != null) {
                lore.add(LegacyText.YELLOW + "Left-click: " + LegacyText.GRAY + "Toggle assignment");
                lore.add(LegacyText.YELLOW + "Right-click: " + LegacyText.GRAY + "Edit shared ability");
                lore.addAll(iconHints());
            }
            if (attack == null)
                inventory.setItem(i % PAGE_SIZE, item(Material.BARRIER, key + " (missing)", lore));
            else
                inventory.setItem(i % PAGE_SIZE, abilityItem(attack, attack.name(), lore));
        }
        inventory.setItem(46,
                item(Material.RED_DYE, "Use More Often",
                        List.of(LegacyText.YELLOW + "Click: " + LegacyText.GRAY + "Decrease interval by 1 second",
                                LegacyText.DARK_GRAY + "Shift-click: " + LegacyText.GRAY + "Five seconds")));
        inventory.setItem(48,
                item(Material.CLOCK, "Interval: about " + holder.options().intervalSeconds() + " Seconds",
                        List.of("Mixes ready attacks with weapon combat", "Each NPC has independent cooldowns",
                                "Requires Max Health above 0")));
        inventory.setItem(50,
                item(Material.LIME_DYE, "Use Less Often",
                        List.of(LegacyText.YELLOW + "Click: " + LegacyText.GRAY + "Increase interval by 1 second",
                                LegacyText.DARK_GRAY + "Shift-click: " + LegacyText.GRAY + "Five seconds")));
        inventory.setItem(51,
                item(Material.BOOK, "Manage Ability Library", List.of("Create and edit attacks for all NPCs")));
        footer(inventory, page, keys.size());
        show(player, inventory);
    }
    private List<String> assignmentKeys(SpecialAttackOptions options) {
        List<String> keys = new ArrayList<>(repository.findAll().stream().map(FighterAttack::key).toList());
        options.assignedAttackKeys().stream().filter(key -> !keys.contains(key)).sorted().forEach(keys::add);
        return keys;
    }

    private void edit(Player player, EditHolder holder) {
        FighterAttack attack = repository.find(holder.key()).orElse(null);
        if (attack == null) {
            holder.back().accept(player);
            return;
        }
        Inventory inventory = menu(holder, "Abilities · " + attack.name());
        inventory.setItem(0,
                item(Material.COMPASS, "Shape & Origin", List.of("Choose where and how the attack is cast")));
        inventory.setItem(1, item(Material.CLOCK, "Timing", List.of("Instant, delayed, or charged for the next hit")));
        inventory.setItem(2, item(Material.IRON_SWORD, "Damage & Effects",
                List.of("Combine damage, debuffs, healing, and knockback")));
        inventory.setItem(3,
                item(Material.FIREWORK_STAR, "Visuals", List.of("Choose the attack's particles and sound")));
        inventory.setItem(4, item(Material.ENDER_EYE, "Preview",
                List.of("Shows the shape from your position and view", "Preview does not damage or teleport anyone")));
        switch (holder.tab()) {
            case OVERVIEW -> {
                inventory.setItem(13, abilityItem(attack, attack.name(), iconLore(attack)));
                inventory.setItem(20, item(Material.NAME_TAG, "Rename",
                        List.of("Display name; existing NPC assignments are preserved")));
                inventory.setItem(22, item(Material.WRITABLE_BOOK, "Duplicate",
                        List.of("Create an independent copy of this attack")));
                inventory.setItem(24,
                        item(Material.BOOK, "Shared Ability",
                                List.of("Edits apply to every NPC assigned this attack",
                                        "Use the tabs above to configure it",
                                        "NPC usage frequency is set on the assignment screen")));
                inventory.setItem(53, item(Material.TNT, "Delete Ability", List.of("Requires confirmation")));
            }
            case GEOMETRY -> {
                inventory.setItem(10, item(shapeIcon(attack.shape()), "Shape: " + label(attack.shape()), List.of(
                        shapeDescription(attack.shape()),
                        LegacyText.YELLOW + "Click: " + LegacyText.GRAY + "Cycle sphere, cone, beam, teleport")));
                inventory.setItem(11,
                        item(Material.COMPASS, "Origin: " + label(attack.origin()), List.of(
                                attack.shape() == Shape.SPHERE
                                        ? LegacyText.YELLOW + "Click: " + LegacyText.GRAY
                                                + "Cycle NPC or target position"
                                        : "Casts from the NPC",
                                "NPC spheres follow the caster; target spheres mark a spot")));
                number(inventory, 12, Material.SPYGLASS, "Activation Range", attack.range(), "blocks",
                        "How close the opponent must be to start casting");
                if (attack.shape() != Shape.CONE)
                    number(inventory, 13, Material.SLIME_BALL,
                            attack.shape() == Shape.TELEPORT
                                    ? "Blink Distance"
                                    : attack.shape() == Shape.BEAM ? "Beam Radius" : "Area Radius",
                            attack.size(), "blocks", "Size of the affected area");
                if (attack.shape() == Shape.CONE) {
                    number(inventory, 14, Material.BLAZE_POWDER, "Cone Angle", attack.angle(), "degrees",
                            "Aimed at the opponent when casting starts");
                    number(inventory, 15, Material.SPYGLASS, "Cone Length", attack.coneLength(), "blocks",
                            "How far the cone reaches; separate from activation range");
                }
                inventory.setItem(22,
                        item(Material.BOOK, "Casting",
                                List.of("Cones and beams lock their aim when casting starts",
                                        "Beams stop at blocks; attacks require line of sight",
                                        "Teleport chooses a nearby safe landing spot")));
            }
            case TIMING -> {
                if (attack.castMode() != CastMode.NEXT_ATTACK)
                    number(inventory, 11, Material.CLOCK, "Cast Delay", attack.delayTicks() / 20.0, "seconds",
                            "Changing this selects instant (0) or delayed casting");
                number(inventory, 13, Material.REPEATER, "Cooldown", attack.cooldownTicks() / 20.0, "seconds",
                        "For charges, starts again when the charge is released");
                inventory.setItem(15,
                        item(modeIcon(attack.castMode()), "Cast Mode: " + modeLabel(attack.castMode()),
                                List.of(modeDescription(attack.castMode()),
                                        LegacyText.YELLOW + "Click: " + LegacyText.GRAY
                                                + "Cycle Instant / Delayed / On Next Attack",
                                        "Delayed defaults to 1 second; charges expire after 30 seconds")));
            }
            case EFFECTS -> {
                number(inventory, 10, Material.IRON_SWORD, "Damage", attack.damage(), "HP",
                        "2 HP = 1 heart; 0 allows attacks with effects only");
                number(inventory, 12, Material.CLOCK, "Effect Duration", attack.effectSeconds(), "seconds",
                        "Duration of fire and potion effects");
                number(inventory, 14, Material.POTION, "Potion Level", attack.effectLevel(), "",
                        "Applies to enabled potion effects");
                number(inventory, 16, Material.FEATHER, "Knockback Strength", attack.knockback(), "",
                        "Used when Knockback is enabled");
                for (Effect effect : Effect.values()) {
                    boolean enabled = attack.effects().contains(effect);
                    inventory.setItem(19 + effect.ordinal(),
                            item(enabled ? Material.LIME_DYE : Material.GRAY_DYE, label(effect),
                                    List.of(enabled ? LegacyText.GREEN + "Enabled" : LegacyText.RED + "Disabled",
                                            LegacyText.YELLOW + "Click: " + LegacyText.GRAY + "Toggle effect")));
                }
                inventory.setItem(31,
                        item(Material.BOOK, "Combine Effects", List.of("Toggle any combination of effects",
                                "Life Drain heals by damage actually dealt", "Teleport only repositions the caster",
                                "Cancelled damage also prevents secondary effects")));
            }
            case VISUALS -> inventory.setItem(13,
                    item(AbilityVisuals.icon(attack.visual()), "Particles: " + label(attack.visual()),
                            List.of(LegacyText.YELLOW + "Click: " + LegacyText.GRAY + "Cycle particle themes",
                                    AbilityVisuals.description(attack.visual()),
                                    "Visuals only; damage and effects are set separately")));
        }
        inventory.setItem(45, item(Material.BOOK, "Overview", List.of()));
        inventory.setItem(49, item(Material.BARRIER, "Back to Library", List.of()));
        show(player, inventory);
    }
    private void confirmDelete(Player player, EditHolder holder) {
        Inventory inventory = menu(new DeleteHolder(holder.key(), holder.back()), "Delete Shared Ability?");
        inventory.setItem(11, item(Material.TNT, "Delete", List.of("Assigned NPCs will stop using this attack",
                "The definition will be removed from the library")));
        inventory.setItem(15, item(Material.BARRIER, "Cancel", List.of()));
        show(player, inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getView().getTopInventory().getHolder() instanceof Holder holder))
            return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize())
            return;
        if (holder instanceof SelectHolder select) {
            List<FighterAttack> attacks = repository.findAll();
            int index = select.page() * PAGE_SIZE + slot;
            if (slot < PAGE_SIZE && index < attacks.size() && event.isLeftClick())
                select.select().accept(attacks.get(index).key());
            else if (slot == 49)
                select.back().accept(player);
            else if (slot == 45 || slot == 53)
                selector(player,
                        new SelectHolder(select.page() + (slot == 45 ? -1 : 1), select.select(), select.back()));
        } else if (holder instanceof LibraryHolder library) {
            List<FighterAttack> attacks = repository.findAll();
            if (slot < PAGE_SIZE && library.page() * PAGE_SIZE + slot < attacks.size()) {
                FighterAttack attack = attacks.get(library.page() * PAGE_SIZE + slot);
                if (isIconClick(event)) {
                    repository.save(attack.withIcon(player.getInventory().getItemInMainHand()));
                    library(player, library.page(), library.back());
                } else
                    edit(player, new EditHolder(attack.key(), Tab.OVERVIEW,
                            p -> library(p, library.page(), library.back())));
            } else if (slot == 51)
                templates(player, p -> library(p, library.page(), library.back()));
            else if (slot == 49)
                library.back().accept(player);
            else if (slot == 45 || slot == 53)
                library(player, library.page() + (slot == 45 ? -1 : 1), library.back());
        } else if (holder instanceof TemplateHolder template) {
            if (slot == 49)
                template.back().accept(player);
            else if (slot < FighterTemplates.defaults().size())
                create(player, FighterTemplates.defaults().get(slot), template.back());
        } else if (holder instanceof AssignHolder assign) {
            List<String> keys = assignmentKeys(assign.options());
            int index = assign.page() * PAGE_SIZE + slot;
            if (slot < PAGE_SIZE && index < keys.size()) {
                String key = keys.get(index);
                if (isIconClick(event) && repository.find(key).isPresent()) {
                    repository.save(
                            repository.find(key).orElseThrow().withIcon(player.getInventory().getItemInMainHand()));
                    assignments(player, assign);
                } else if (event.isRightClick() && repository.find(key).isPresent())
                    edit(player, new EditHolder(key, Tab.OVERVIEW, p -> assignments(p, assign)));
                else
                    saveAssignment(player, assign, assign.options().toggle(key));
            } else if (slot == 49)
                assign.back().accept(player);
            else if (slot == 51)
                library(player, 0, p -> assignments(p, assign));
            else if (slot == 46 || slot == 50)
                saveAssignment(player, assign, assign.options().withIntervalSeconds(
                        assign.options().intervalSeconds() + (slot == 46 ? -1 : 1) * (event.isShiftClick() ? 5 : 1)));
            else if (slot == 45 || slot == 53)
                assignments(player, new AssignHolder(assign.options(), assign.save(), assign.back(),
                        assign.page() + (slot == 45 ? -1 : 1)));
        } else if (holder instanceof DeleteHolder delete) {
            if (slot == 11) {
                repository.delete(delete.key());
                delete.back().accept(player);
            } else if (slot == 15)
                edit(player, new EditHolder(delete.key(), Tab.OVERVIEW, delete.back()));
        } else if (holder instanceof EditHolder edit)
            editClick(event, player, edit);
    }
    private void editClick(InventoryClickEvent event, Player player, EditHolder holder) {
        int slot = event.getRawSlot();
        FighterAttack attack = repository.find(holder.key()).orElse(null);
        if (attack == null) {
            holder.back().accept(player);
            return;
        }
        if (slot <= 3) {
            edit(player, new EditHolder(holder.key(), Tab.values()[slot + 1], holder.back()));
            return;
        }
        if (slot == 4) {
            player.closeInventory();
            NpcSpecialAttackService.preview(player, attack);
            player.sendMessage(UiText.info("Previewing " + attack.name() + ". Use /bf abilities to continue editing."));
            return;
        }
        if (slot == 49) {
            holder.back().accept(player);
            return;
        }
        if (slot == 45) {
            edit(player, new EditHolder(holder.key(), Tab.OVERVIEW, holder.back()));
            return;
        }
        if (holder.tab() == Tab.OVERVIEW) {
            if (slot == 13 && isIconClick(event))
                change(player, holder, a -> a.withIcon(player.getInventory().getItemInMainHand()));
            else if (slot == 53)
                confirmDelete(player, holder);
            else if (slot == 22)
                create(player, attack, holder.back());
            else if (slot == 20)
                input.request(player, "Enter the ability display name:",
                        name -> change(player, holder, a -> a.withName(name)), () -> edit(player, holder));
            return;
        }
        if (holder.tab() == Tab.GEOMETRY) {
            if (slot == 10)
                change(player, holder,
                        a -> a.withGeometry(a.origin(), a.shape().next(), a.range(), a.size(), a.angle()));
            else if (slot == 11 && attack.shape() == Shape.SPHERE)
                change(player, holder,
                        a -> a.withGeometry(a.origin().next(), a.shape(), a.range(), a.size(), a.angle()));
            else if (slot == 12)
                numeric(event, player, holder, attack.range(), 1,
                        value -> a -> a.withGeometry(a.origin(), a.shape(), value, a.size(), a.angle()));
            else if (slot == 13 && attack.shape() != Shape.CONE)
                numeric(event, player, holder, attack.size(), 0.25,
                        value -> a -> a.withGeometry(a.origin(), a.shape(), a.range(), value, a.angle()));
            else if (slot == 14 && attack.shape() == Shape.CONE)
                numeric(event, player, holder, attack.angle(), 5,
                        value -> a -> a.withGeometry(a.origin(), a.shape(), a.range(), a.size(), value));
            else if (slot == 15 && attack.shape() == Shape.CONE)
                numeric(event, player, holder, attack.coneLength(), 1, value -> a -> a.withConeLength(value));
        } else if (holder.tab() == Tab.TIMING) {
            if (slot == 11 && attack.castMode() != CastMode.NEXT_ATTACK)
                numeric(event, player, holder, attack.delayTicks() / 20.0, 0.25,
                        value -> a -> a.withTiming((int) Math.round(value * 20), a.cooldownTicks()));
            else if (slot == 13)
                numeric(event, player, holder, attack.cooldownTicks() / 20.0, 1, value -> a -> a
                        .withTiming(a.delayTicks(), (int) Math.round(value * 20)).withCastMode(a.castMode()));
            else if (slot == 15)
                change(player, holder, a -> a.withCastMode(a.castMode().next()));
        } else if (holder.tab() == Tab.EFFECTS) {
            if (slot >= 19 && slot < 19 + Effect.values().length) {
                Effect effect = Effect.values()[slot - 19];
                change(player, holder, a -> {
                    Set<Effect> effects = EnumSet.noneOf(Effect.class);
                    effects.addAll(a.effects());
                    if (!effects.remove(effect))
                        effects.add(effect);
                    return a.withEffects(a.damage(), effects, a.effectSeconds(), a.effectLevel(), a.knockback());
                });
            } else if (slot == 10)
                numeric(event, player, holder, attack.damage(), 0.5, value -> a -> a.withEffects(value, a.effects(),
                        a.effectSeconds(), a.effectLevel(), a.knockback()));
            else if (slot == 12)
                numeric(event, player, holder, attack.effectSeconds(), 1, value -> a -> a.withEffects(a.damage(),
                        a.effects(), (int) value, a.effectLevel(), a.knockback()));
            else if (slot == 14)
                numeric(event, player, holder, attack.effectLevel(), 1, value -> a -> a.withEffects(a.damage(),
                        a.effects(), a.effectSeconds(), (int) value, a.knockback()));
            else if (slot == 16)
                numeric(event, player, holder, attack.knockback(), 0.1, value -> a -> a.withEffects(a.damage(),
                        a.effects(), a.effectSeconds(), a.effectLevel(), value));
        } else if (holder.tab() == Tab.VISUALS && slot == 13)
            change(player, holder, a -> a.withVisual(a.visual().next()));
    }
    private void numeric(InventoryClickEvent event, Player player, EditHolder holder, double current, double step,
            DoubleFunction<UnaryOperator<FighterAttack>> update) {
        if (event.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE) {
            input.request(player, "Enter a numeric value:", value -> {
                try {
                    double number = Double.parseDouble(value);
                    if (!Double.isFinite(number) || Math.abs(number) > 10000)
                        throw new NumberFormatException();
                    change(player, holder, update.apply(number));
                } catch (NumberFormatException error) {
                    player.sendMessage(UiText.error("Enter a finite number between -10000 and 10000."));
                    edit(player, holder);
                }
            }, () -> edit(player, holder));
        } else {
            int direction = NumericControl.direction(event.getClick());
            if (direction != 0)
                change(player, holder, update.apply(current + step * direction * (event.isShiftClick() ? 5 : 1)));
        }
    }
    private void change(Player player, EditHolder holder, UnaryOperator<FighterAttack> update) {
        repository.find(holder.key()).ifPresent(a -> repository.save(update.apply(a)));
        edit(player, holder);
    }
    private void create(Player player, FighterAttack template, Consumer<Player> back) {
        input.request(player, "Enter a name for the new ability:", name -> {
            String key = FighterAttack.normalizeKey(name);
            if (key.isEmpty()) {
                player.sendMessage(UiText.error("Use a name containing letters or numbers."));
                templates(player, back);
                return;
            }
            String base = key.substring(0, Math.min(58, key.length()));
            int suffix = 2;
            while (repository.find(key).isPresent())
                key = base + "_" + suffix++;
            FighterAttack created = template.copy(key, name);
            repository.save(created);
            edit(player, new EditHolder(key, Tab.OVERVIEW, back));
        }, () -> back.accept(player));
    }
    private void saveAssignment(Player player, AssignHolder holder, SpecialAttackOptions options) {
        holder.save().accept(options);
        assignments(player, new AssignHolder(options, holder.save(), holder.back(), holder.page()));
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder)
            event.setCancelled(true);
    }
    private static int page(int requested, int size) {
        return Math.clamp(requested, 0, Math.max(0, (size - 1) / PAGE_SIZE));
    }
    private static Inventory menu(Holder holder, String title) {
        return Bukkit.createInventory(holder, 54, UiText.title(title));
    }
    private static void show(Player player, Inventory inventory) {
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }
    private static void footer(Inventory inventory, int page, int size) {
        if (page > 0)
            inventory.setItem(45, item(Material.ARROW, "Previous Page", List.of()));
        inventory.setItem(49, item(Material.BARRIER, "Back", List.of()));
        if ((page + 1) * PAGE_SIZE < size)
            inventory.setItem(53, item(Material.ARROW, "Next Page", List.of()));
    }
    private static void number(Inventory inventory, int slot, Material icon, String label, double value, String unit,
            String help) {
        List<String> lore = new ArrayList<>(List.of(help));
        lore.addAll(NumericControl.lore());
        inventory.setItem(slot,
                item(icon, label + ": " + String.format(java.util.Locale.ROOT, "%.2f", value) + " " + unit, lore));
    }
    private static List<String> summary(FighterAttack attack) {
        List<String> lore = new ArrayList<>(List.of(
                LegacyText.GRAY + label(attack.shape()) + " · from " + label(attack.origin()),
                LegacyText.GRAY + "Range: " + attack.range() + " blocks · Damage: " + attack.damage() + " HP",
                LegacyText.GRAY + "Cast: " + modeLabel(attack.castMode())
                        + (attack.castMode() == CastMode.DELAYED ? " (" + attack.delayTicks() / 20.0 + "s)" : "")
                        + " · Cooldown: " + attack.cooldownTicks() / 20.0 + "s",
                LegacyText.GRAY + "Effects: "
                        + (attack.effects().isEmpty()
                                ? "None"
                                : attack.effects().stream().sorted().map(AbilitiesGuiService::label)
                                        .collect(java.util.stream.Collectors.joining(", ")))));
        if (attack.shape() == Shape.CONE)
            lore.add(1, LegacyText.GRAY + "Cone: " + attack.coneLength() + " blocks · " + attack.angle() + " degrees");
        return lore;
    }
    private static String label(Enum<?> value) {
        String name = value.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
    private static boolean isIconClick(InventoryClickEvent event) {
        return event.getClick() == org.bukkit.event.inventory.ClickType.DROP
                || event.getClick() == org.bukkit.event.inventory.ClickType.CONTROL_DROP;
    }

    private static List<String> iconHints() {
        return List.of(LegacyText.GOLD + "Q / Drop: " + LegacyText.GRAY + "Set icon from main-hand item",
                LegacyText.DARK_GRAY + "Empty hand: " + LegacyText.GRAY + "Restore the default icon");
    }

    private static List<String> iconLore(FighterAttack attack) {
        List<String> lore = summary(attack);
        lore.addAll(iconHints());
        return lore;
    }

    private static ItemStack abilityItem(FighterAttack attack, String name, List<String> lore) {
        ItemStack icon = attack.icon();
        if (icon == null)
            icon = new ItemStack(icon(attack));
        return decorate(icon, name, lore);
    }

    private static Material icon(FighterAttack attack) {
        if (attack.shape() == Shape.TELEPORT)
            return Material.ENDER_PEARL;
        return AbilityVisuals.icon(attack.visual());
    }
    private static Material shapeIcon(Shape shape) {
        return switch (shape) {
            case SPHERE -> Material.SLIME_BALL;
            case CONE -> Material.HOPPER;
            case BEAM -> Material.END_ROD;
            case TELEPORT -> Material.ENDER_PEARL;
        };
    }
    private static String shapeDescription(Shape shape) {
        return switch (shape) {
            case SPHERE -> "A round area around the NPC or a marked target position";
            case CONE -> "A widening cone aimed forwards from the NPC";
            case BEAM -> "A narrow beam from the NPC that stops at blocks";
            case TELEPORT -> "Move the NPC to a nearby safe landing spot";
        };
    }
    private static String modeLabel(CastMode mode) {
        return switch (mode) {
            case INSTANT -> "Instant";
            case DELAYED -> "Delayed";
            case NEXT_ATTACK -> "On Next Attack";
        };
    }
    private static Material modeIcon(CastMode mode) {
        return switch (mode) {
            case INSTANT -> Material.LIGHTNING_ROD;
            case DELAYED -> Material.CLOCK;
            case NEXT_ATTACK -> Material.ENCHANTED_BOOK;
        };
    }
    private static String modeDescription(CastMode mode) {
        return switch (mode) {
            case INSTANT -> "Release the ability immediately";
            case DELAYED -> "Pause and show casting particles before releasing";
            case NEXT_ATTACK -> "Charge up; release after a valid weapon hit deals damage";
        };
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        return decorate(new ItemStack(material), name, lore);
    }

    private static ItemStack decorate(ItemStack item, String name, List<String> lore) {
        var meta = item.getItemMeta();
        meta.displayName(LegacyText.component(LegacyText.WHITE + name));
        meta.lore(lore.stream().map(line -> LegacyText.component(LegacyText.GRAY + line)).toList());
        item.setItemMeta(meta);
        return item;
    }
}

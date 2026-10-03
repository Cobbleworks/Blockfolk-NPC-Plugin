package dev.blockfolk.gui;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.IntConsumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
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
    private static final int PREVIEW_SLOT = 7;
    private static final String CLICK_SOUND = "minecraft:ui.button.click";
    private final FighterAttackRepository repository;
    private final ChatInputService input;
    private final Consumer<Player> mainMenu;

    private enum Tab {
        OVERVIEW(Material.BOOK, "Overview", "Name, icon, duplicate, and delete"),
        SHAPE(Material.COMPASS, "Shape & Range", "Where the ability strikes and how far it reaches"),
        TIMING(Material.CLOCK, "Timing & Triggers", "Cast mode, cooldown, pulses, and when NPCs use it"),
        EFFECTS(Material.IRON_SWORD, "Damage & Effects", "Damage, afflictions, crowd control, and caster buffs"),
        VISUALS(Material.FIREWORK_STAR, "Visuals", "Particle theme and impact sound");

        private final Material icon;
        private final String title;
        private final String description;

        Tab(Material icon, String title, String description) {
            this.icon = icon;
            this.title = title;
            this.description = description;
        }
        int slot() {
            return ordinal() + 1;
        }
    }
    private interface Holder extends InventoryHolder {
        @Override
        default Inventory getInventory() {
            return null;
        }
    }
    /** Click handler registered by the screen that rendered the slot. */
    @FunctionalInterface
    private interface Action {
        void run(InventoryClickEvent event, Player player);
    }
    private record LibraryHolder(int page, Consumer<Player> back) implements Holder {
    }
    private record SelectHolder(int page, Consumer<String> select, Consumer<Player> back,
            boolean assignment) implements Holder {
    }
    private record TemplateHolder(Consumer<Player> back) implements Holder {
    }
    private record EditHolder(String key, Tab tab, Consumer<Player> back, Map<Integer, Action> actions)
            implements Holder {
        EditHolder(String key, Tab tab, Consumer<Player> back) {
            this(key, tab, back, new HashMap<>());
        }
    }
    private record DeleteHolder(String key, Consumer<Player> back) implements Holder {
    }
    private record AssignHolder(SpecialAttackOptions options, Consumer<SpecialAttackOptions> save,
            Consumer<Player> back, int page) implements Holder {
    }
    private record Screen(Inventory inventory, Map<Integer, Action> actions) {
        void button(int slot, ItemStack item, Action action) {
            inventory.setItem(slot, item);
            actions.put(slot, action);
        }
        void set(int slot, ItemStack item) {
            inventory.setItem(slot, item);
        }
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
        selector(player, new SelectHolder(0, select, back, false));
    }
    public String abilityName(String key) {
        return repository.find(key).map(FighterAttack::name).orElse(key + " (missing)");
    }

    private void selector(Player player, SelectHolder holder) {
        List<FighterAttack> attacks = repository.findAll();
        int page = page(holder.page(), attacks.size());
        Inventory inventory = menu(new SelectHolder(page, holder.select(), holder.back(), holder.assignment()),
                holder.assignment() ? "Abilities · Assign Ability" : "Use Ability · Choose Ability");
        for (int i = page * PAGE_SIZE; i < Math.min(attacks.size(), (page + 1) * PAGE_SIZE); i++) {
            FighterAttack attack = attacks.get(i);
            List<String> lore = summary(attack);
            lore.add(hint("Left-click", holder.assignment()
                    ? "Assign this ability to the NPC"
                    : "Use this ability in the action"));
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
            lore.add(hint("Left-click", "Edit this shared ability"));
            lore.add(hint("Shift-right-click", "Delete this shared ability"));
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
            List<String> lore = summary(attack);
            lore.add(hint("Click", "Start from this template"));
            inventory.setItem(i, abilityItem(attack, attack.name(), lore));
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
            List<String> lore = attack == null
                    ? new ArrayList<>(List.of(LegacyText.RED + "Definition was deleted; right-click to unassign"))
                    : summary(attack);
            lore.add(0, LegacyText.GREEN + "✔ Assigned");
            lore.add(hint("Right-click", "Remove assignment"));
            if (attack != null) {
                lore.add(hint("Left-click", "Edit shared ability"));
                lore.addAll(iconHints());
            }
            if (attack == null)
                inventory.setItem(i % PAGE_SIZE, item(Material.BARRIER, key + " (missing)", lore));
            else
                inventory.setItem(i % PAGE_SIZE, abilityItem(attack, attack.name(), lore));
        }
        if (keys.isEmpty())
            inventory.setItem(22, item(Material.BARRIER, "No Assigned Abilities",
                    List.of("Use Assign Ability to choose an attack from the library")));
        inventory.setItem(46,
                item(Material.EMERALD, "Assign Ability", List.of("Choose an ability from the shared library")));
        List<String> intervalLore = new ArrayList<>(List.of("Mixes ready attacks with weapon combat",
                "Each NPC has independent cooldowns", "Abilities whose trigger is met are preferred",
                "Requires Max Health above 0", hint("Left-click", "Decrease interval by 1 second"),
                hint("Right-click", "Increase interval by 1 second"),
                LegacyText.DARK_GRAY + "Shift-click: " + LegacyText.GRAY + "Five seconds"));
        inventory.setItem(48, item(Material.CLOCK, "Interval: about " + holder.options().intervalSeconds() + " Seconds",
                intervalLore));
        inventory.setItem(50,
                item(Material.BOOK, "Manage Ability Library", List.of("Create and edit attacks for all NPCs")));
        footer(inventory, page, keys.size());
        show(player, inventory);
    }
    private List<String> assignmentKeys(SpecialAttackOptions options) {
        Set<String> assigned = options.assignedAttackKeys();
        List<String> keys = new ArrayList<>(
                repository.findAll().stream().map(FighterAttack::key).filter(assigned::contains).toList());
        assigned.stream().filter(key -> !keys.contains(key)).sorted().forEach(keys::add);
        return keys;
    }

    private void edit(Player player, EditHolder request) {
        FighterAttack attack = repository.find(request.key()).orElse(null);
        if (attack == null) {
            request.back().accept(player);
            return;
        }
        EditHolder holder = new EditHolder(request.key(), request.tab(), request.back());
        Inventory inventory = menu(holder, "Abilities · " + attack.name());
        Screen screen = new Screen(inventory, holder.actions());
        for (Tab tab : Tab.values()) {
            boolean open = tab == holder.tab();
            ItemStack item = item(tab.icon, tab.title, List.of(tab.description,
                    open ? LegacyText.GREEN + "▶ Open" : hint("Click", "Open this tab")));
            screen.button(tab.slot(), open ? glint(item) : item,
                    (event, p) -> edit(p, new EditHolder(holder.key(), tab, holder.back())));
        }
        screen.button(PREVIEW_SLOT, item(Material.ENDER_EYE, "Preview",
                List.of("Shows the shape from your position and view", "Preview does not damage or teleport anyone")),
                (event, p) -> {
                    p.closeInventory();
                    NpcSpecialAttackService.preview(p, attack);
                    p.sendMessage(UiText.info("Previewing " + attack.name() + ". Use /bf abilities to continue editing."));
                });
        switch (holder.tab()) {
            case OVERVIEW -> overviewTab(screen, holder, attack);
            case SHAPE -> shapeTab(screen, holder, attack);
            case TIMING -> timingTab(screen, holder, attack);
            case EFFECTS -> effectsTab(screen, holder, attack);
            case VISUALS -> visualsTab(screen, holder, attack);
        }
        screen.button(49, item(Material.BARRIER, "Back", List.of()), (event, p) -> holder.back().accept(p));
        GuiLayout.fillRow(inventory, 0);
        show(player, inventory);
    }

    private void overviewTab(Screen screen, EditHolder holder, FighterAttack attack) {
        screen.button(13, abilityItem(attack, attack.name(), iconLore(attack)), (event, p) -> {
            if (isIconClick(event))
                change(p, holder, a -> a.withIcon(p.getInventory().getItemInMainHand()));
        });
        screen.button(20, item(Material.NAME_TAG, "Rename",
                List.of("Display name; existing NPC assignments are preserved")),
                (event, p) -> input.request(p, "Enter the ability display name:",
                        name -> change(p, holder, a -> a.withName(name)), () -> edit(p, holder)));
        screen.button(22, item(Material.WRITABLE_BOOK, "Duplicate", List.of("Create an independent copy of this attack")),
                (event, p) -> create(p, attack, holder.back()));
        screen.set(24, item(Material.BOOK, "Shared Ability", List.of("Edits apply to every NPC assigned this attack",
                "Use the tabs above to configure it", "NPC usage frequency is set on the assignment screen")));
        screen.button(53, item(Material.TNT, "Delete Ability", List.of("Requires confirmation")),
                (event, p) -> confirmDelete(p, holder));
    }

    private void shapeTab(Screen screen, EditHolder holder, FighterAttack attack) {
        Shape[] shapes = Shape.values();
        for (int i = 0; i < shapes.length; i++) {
            Shape shape = shapes[i];
            choice(screen, holder, 9 + i, shapeIcon(shape), label(shape), "Shape", shapeDescription(shape),
                    attack.shape() == shape, a -> a.toBuilder().shape(shape).build());
        }
        List<IntConsumer> controls = new ArrayList<>();
        Shape shape = attack.shape();
        if (shape.supportsOrigin())
            controls.add(slot -> screen.button(slot,
                    item(attack.origin() == Origin.NPC ? Material.ARMOR_STAND : Material.RECOVERY_COMPASS,
                            "Centre: " + (attack.origin() == Origin.NPC ? "Caster" : "Target"),
                            List.of(attack.origin() == Origin.NPC
                                    ? "The area follows the NPC while it casts"
                                    : "The area marks the opponent's position", hint("Click", "Switch centre"))),
                    (event, p) -> change(p, holder, a -> a.toBuilder().origin(a.origin().next()).build())));
        controls.add(slot -> number(screen, holder, slot, Material.SPYGLASS, "Activation Range", attack.range(),
                "blocks", 1, FighterAttack.MAX_RANGE, 1, rangeHelp(shape), v -> a -> a.toBuilder().range(v).build()));
        controls.add(slot -> number(screen, holder, slot, Material.LEAD, "Minimum Range", attack.minRange(), "blocks",
                0, attack.range() - 1, 1, "Automatic casts wait until the opponent is this far; 0 disables",
                v -> a -> a.toBuilder().minRange(v).build()));
        String sizeLabel = switch (shape) {
            case SPHERE -> "Area Radius";
            case RING -> "Outer Radius";
            case BEAM -> "Beam Radius";
            case DASH -> "Path Radius";
            case CHAIN -> "Jump Radius";
            case TELEPORT -> "Blink Distance";
            case CONE, SELF -> null;
        };
        if (sizeLabel != null) {
            boolean wholeBlocks = shape == Shape.RING || shape == Shape.CHAIN || shape == Shape.TELEPORT;
            controls.add(slot -> number(screen, holder, slot, Material.SLIME_BALL, sizeLabel, attack.size(), "blocks",
                    wholeBlocks ? 1 : 0.25, FighterAttack.MAX_SIZE, 0.25, sizeHelp(shape),
                    v -> a -> a.toBuilder().size(v).build()));
        }
        if (shape == Shape.RING)
            controls.add(slot -> number(screen, holder, slot, Material.HEART_OF_THE_SEA, "Safe Inner Radius",
                    attack.innerRadius(), "blocks", 0.5, attack.size() - 0.5, 0.25,
                    "Entities closer to the centre than this are not hit",
                    v -> a -> a.toBuilder().innerRadius(v).build()));
        if (shape == Shape.CONE) {
            controls.add(slot -> number(screen, holder, slot, Material.BLAZE_POWDER, "Cone Angle", attack.angle(),
                    "degrees", 10, 160, 5, "Aimed at the opponent when casting starts",
                    v -> a -> a.toBuilder().angle(v).build()));
            controls.add(slot -> number(screen, holder, slot, Material.SPYGLASS, "Cone Length", attack.coneLength(),
                    "blocks", 1, FighterAttack.MAX_RANGE, 1, "How far the cone reaches; separate from activation range",
                    v -> a -> a.toBuilder().coneLength(v).build()));
        }
        if (shape == Shape.CHAIN)
            controls.add(slot -> number(screen, holder, slot, Material.IRON_CHAIN, "Chain Targets",
                    attack.chainTargets(), "targets", FighterAttack.MIN_CHAIN_TARGETS, FighterAttack.MAX_CHAIN_TARGETS,
                    1, "Maximum entities struck, including the first",
                    v -> a -> a.toBuilder().chainTargets((int) Math.round(v)).build()));
        place(controls, 3);
        screen.set(40, item(Material.BOOK, "How " + label(shape) + " Works", shapeTips(shape)));
    }

    private void timingTab(Screen screen, EditHolder holder, FighterAttack attack) {
        CastMode[] modes = CastMode.values();
        for (int i = 0; i < modes.length; i++) {
            CastMode mode = modes[i];
            choice(screen, holder, 11 + i * 2, modeIcon(mode), modeLabel(mode), "Cast Mode", modeDescription(mode),
                    attack.castMode() == mode, a -> a.withCastMode(mode));
        }
        List<IntConsumer> controls = new ArrayList<>();
        if (attack.castMode() != CastMode.NEXT_ATTACK)
            controls.add(slot -> number(screen, holder, slot, Material.CLOCK, "Cast Delay", attack.delayTicks() / 20.0,
                    "seconds", 0, FighterAttack.MAX_DELAY_TICKS / 20.0, 0.25,
                    "Warning time before release; 0 selects Instant",
                    v -> a -> a.withTiming((int) Math.round(v * 20), a.cooldownTicks())));
        controls.add(slot -> number(screen, holder, slot, Material.REPEATER, "Cooldown", attack.cooldownTicks() / 20.0,
                "seconds", FighterAttack.MIN_COOLDOWN_TICKS / 20.0, FighterAttack.MAX_COOLDOWN_TICKS / 20.0, 1,
                "Counted after the cast delay; charges restart it on release",
                v -> a -> a.toBuilder().cooldownTicks((int) Math.round(v * 20)).build()));
        if (attack.shape().supportsPulses()) {
            controls.add(slot -> number(screen, holder, slot, Material.BELL, "Pulses", attack.pulses(), "",
                    1, FighterAttack.MAX_PULSES, 1, "Strikes again at the same spot and aim; 1 strikes once",
                    v -> a -> a.toBuilder().pulses((int) Math.round(v)).build()));
            if (attack.pulses() > 1)
                controls.add(slot -> number(screen, holder, slot, Material.COMPARATOR, "Pulse Interval",
                        attack.pulseIntervalTicks() / 20.0, "seconds", FighterAttack.MIN_PULSE_INTERVAL_TICKS / 20.0,
                        FighterAttack.MAX_PULSE_INTERVAL_TICKS / 20.0, 0.25, "Time between lingering strikes",
                        v -> a -> a.toBuilder().pulseIntervalTicks((int) Math.round(v * 20)).build()));
        } else
            controls.add(slot -> screen.set(slot, item(Material.GRAY_DYE, "Pulses: Not available",
                    List.of(label(attack.shape()) + " abilities strike once",
                            "Sphere, ring, cone, and beam can linger"))));
        place(controls, 3);
        Condition[] conditions = Condition.values();
        for (int i = 0; i < conditions.length; i++) {
            Condition condition = conditions[i];
            choice(screen, holder, 37 + i * 2, conditionIcon(condition), conditionLabel(condition), "Trigger",
                    conditionDescription(condition), attack.condition() == condition,
                    a -> a.toBuilder().condition(condition).build());
        }
    }

    private void effectsTab(Screen screen, EditHolder holder, FighterAttack attack) {
        number(screen, holder, 10, Material.IRON_SWORD, "Damage", attack.damage(), "HP", 0, FighterAttack.MAX_DAMAGE,
                0.5, "2 HP = 1 heart; 0 allows effect-only abilities", v -> a -> a.toBuilder().damage(v).build());
        number(screen, holder, 12, Material.CLOCK, "Effect Duration", attack.effectSeconds(), "seconds", 1,
                FighterAttack.MAX_EFFECT_SECONDS, 1, "Fire, afflictions, and caster buffs",
                v -> a -> a.toBuilder().effectSeconds((int) Math.round(v)).build());
        numberWithDisplay(screen, holder, 14, Material.POTION, "Effect Level", attack.effectLevel(),
                roman(attack.effectLevel()), "I – " + roman(FighterAttack.MAX_EFFECT_LEVEL), 1,
                "Level of afflictions and caster buffs",
                v -> a -> a.toBuilder().effectLevel((int) Math.round(v)).build());
        number(screen, holder, 16, Material.PISTON, "Force", attack.knockback(), "", 0, FighterAttack.MAX_FORCE, 0.1,
                "Strength of Knockback, Pull, and Launch", v -> a -> a.toBuilder().knockback(v).build());
        int row = 2;
        for (EffectCategory category : EffectCategory.values()) {
            screen.set(row * 9, categoryHeader(category, attack));
            int column = 1;
            for (Effect effect : Effect.values()) {
                if (effect.category() == category)
                    effectToggle(screen, holder, row * 9 + column++, effect, attack);
            }
            row++;
        }
    }

    private void effectToggle(Screen screen, EditHolder holder, int slot, Effect effect, FighterAttack attack) {
        boolean enabled = attack.effects().contains(effect);
        List<String> lore = new ArrayList<>(List.of(effectDescription(effect),
                enabled ? LegacyText.GREEN + "✔ Enabled" : LegacyText.RED + "✘ Disabled"));
        if (effect == Effect.KNOCKBACK || effect == Effect.PULL)
            lore.add(LegacyText.DARK_GRAY + "Replaces " + (effect == Effect.PULL ? "Knockback" : "Pull"));
        if (effect.category() != EffectCategory.CASTER && !attack.shape().hitsVictims())
            lore.add(LegacyText.GOLD + "No effect: " + label(attack.shape()) + " does not hit others");
        lore.add(hint("Click", enabled ? "Disable" : "Enable"));
        ItemStack item = item(effectIcon(effect), label(effect), lore);
        screen.button(slot, enabled ? glint(item) : item, (event, p) -> change(p, holder, a -> {
            Set<Effect> effects = EnumSet.noneOf(Effect.class);
            effects.addAll(a.effects());
            if (!effects.remove(effect)) {
                effects.add(effect);
                if (effect == Effect.PULL)
                    effects.remove(Effect.KNOCKBACK);
                else if (effect == Effect.KNOCKBACK)
                    effects.remove(Effect.PULL);
            }
            return a.toBuilder().effects(effects).build();
        }));
    }

    private void visualsTab(Screen screen, EditHolder holder, FighterAttack attack) {
        Visual[] visuals = Visual.values();
        for (int i = 0; i < visuals.length; i++) {
            Visual visual = visuals[i];
            boolean selected = attack.visual() == visual;
            List<String> lore = new ArrayList<>(List.of(AbilityVisuals.description(visual),
                    selected ? LegacyText.GREEN + "✔ Selected" : hint("Click", "Select and hear its impact")));
            ItemStack item = item(AbilityVisuals.icon(visual), label(visual), lore);
            screen.button(9 + i, selected ? glint(item) : item, (event, p) -> {
                p.playSound(p.getLocation(), AbilityVisuals.impactSound(visual), 0.6f, 1.1f);
                if (!selected)
                    change(p, holder, a -> a.withVisual(visual));
            });
        }
        screen.set(40, item(Material.BOOK, "About Visuals",
                List.of("Themes change particles and impact sound only",
                        "Every telegraphed cast plays the same warning sound",
                        "so players learn to react to it", "Use Preview to see the shape in the world")));
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
        if (holder instanceof EditHolder edit) {
            if (repository.find(edit.key()).isEmpty()) {
                edit.back().accept(player);
                return;
            }
            Action action = edit.actions().get(slot);
            if (action != null)
                action.run(event, player);
        } else if (holder instanceof SelectHolder select) {
            List<FighterAttack> attacks = repository.findAll();
            int index = select.page() * PAGE_SIZE + slot;
            if (slot < PAGE_SIZE && index < attacks.size() && event.isLeftClick())
                select.select().accept(attacks.get(index).key());
            else if (slot == 49)
                select.back().accept(player);
            else if (slot == 45 || slot == 53)
                selector(player, new SelectHolder(select.page() + (slot == 45 ? -1 : 1), select.select(), select.back(),
                        select.assignment()));
        } else if (holder instanceof LibraryHolder library) {
            List<FighterAttack> attacks = repository.findAll();
            if (slot < PAGE_SIZE && library.page() * PAGE_SIZE + slot < attacks.size()) {
                FighterAttack attack = attacks.get(library.page() * PAGE_SIZE + slot);
                if (isIconClick(event)) {
                    repository.save(attack.withIcon(player.getInventory().getItemInMainHand()));
                    library(player, library.page(), library.back());
                } else if (event.isShiftClick() && event.isRightClick())
                    confirmDelete(player, new EditHolder(attack.key(), Tab.OVERVIEW,
                            p -> library(p, library.page(), library.back())));
                else if (event.isLeftClick())
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
                } else if (event.isLeftClick() && repository.find(key).isPresent())
                    edit(player, new EditHolder(key, Tab.OVERVIEW, p -> assignments(p, assign)));
                else if (event.isRightClick())
                    saveAssignment(player, assign, assign.options().toggle(key));
            } else if (slot == 49)
                assign.back().accept(player);
            else if (slot == 50)
                library(player, 0, p -> assignments(p, assign));
            else if (slot == 46)
                selector(player,
                        new SelectHolder(0,
                                key -> saveAssignment(player, assign,
                                        assign.options().assignedAttackKeys().contains(key)
                                                ? assign.options()
                                                : assign.options().toggle(key)),
                                p -> assignments(p, assign), true));
            else if (slot == 48) {
                int direction = NumericControl.direction(event.getClick());
                if (direction != 0)
                    saveAssignment(player, assign, assign.options().withIntervalSeconds(
                            assign.options().intervalSeconds() + direction * (event.isShiftClick() ? 5 : 1)));
            } else if (slot == 45 || slot == 53)
                assignments(player, new AssignHolder(assign.options(), assign.save(), assign.back(),
                        assign.page() + (slot == 45 ? -1 : 1)));
        } else if (holder instanceof DeleteHolder delete) {
            if (slot == 11) {
                repository.delete(delete.key());
                delete.back().accept(player);
            } else if (slot == 15)
                edit(player, new EditHolder(delete.key(), Tab.OVERVIEW, delete.back()));
        }
    }

    /** One option of a pick-one group; the selected option glows and says so. */
    private void choice(Screen screen, EditHolder holder, int slot, Material icon, String label, String group,
            String description, boolean selected, UnaryOperator<FighterAttack> select) {
        ItemStack item = item(icon, label, List.of(LegacyText.DARK_GRAY + group, description,
                selected ? LegacyText.GREEN + "✔ Selected" : hint("Click", "Select")));
        screen.button(slot, selected ? glint(item) : item, (event, p) -> {
            if (!selected)
                change(p, holder, select);
        });
    }

    private void number(Screen screen, EditHolder holder, int slot, Material icon, String label, double value,
            String unit, double min, double max, double step, String help,
            DoubleFunction<UnaryOperator<FighterAttack>> update) {
        String suffix = unit.isEmpty() ? "" : " " + unit;
        numberWithDisplay(screen, holder, slot, icon, label, value, format(value) + suffix,
                format(min) + " – " + format(max) + suffix, step, help, update);
    }

    private void numberWithDisplay(Screen screen, EditHolder holder, int slot, Material icon, String label,
            double value, String display, String bounds, double step, String help,
            DoubleFunction<UnaryOperator<FighterAttack>> update) {
        List<String> lore = new ArrayList<>(List.of(help, LegacyText.DARK_GRAY + "Allowed: " + bounds));
        lore.addAll(NumericControl.lore());
        screen.button(slot, item(icon, label + ": " + display, lore),
                (event, p) -> numeric(event, p, holder, value, step, update));
    }

    /** Places controls on a row, evenly spaced and centred. */
    private static void place(List<IntConsumer> controls, int row) {
        int count = Math.min(controls.size(), 5);
        int first = row * 9 + 4 - (count - 1);
        for (int i = 0; i < count; i++)
            controls.get(i).accept(first + i * 2);
    }

    private void numeric(InventoryClickEvent event, Player player, EditHolder holder, double current, double step,
            DoubleFunction<UnaryOperator<FighterAttack>> update) {
        if (event.getClick() == ClickType.MIDDLE) {
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
        player.playSound(player.getLocation(), CLICK_SOUND, 0.35f, 1.6f);
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

    static List<String> summary(FighterAttack attack) {
        List<String> lore = new ArrayList<>();
        Shape shape = attack.shape();
        lore.add(label(shape) + (shape.supportsOrigin()
                ? " · centred on " + (attack.origin() == Origin.NPC ? "caster" : "target")
                : ""));
        String reach = attack.minRange() > 0
                ? format(attack.minRange()) + "–" + format(attack.range())
                : format(attack.range());
        lore.add("Range: " + reach + " blocks"
                + (shape.hitsVictims() ? " · Damage: " + format(attack.damage()) + " HP" : ""));
        String detail = switch (shape) {
            case SPHERE -> "Radius: " + format(attack.size()) + " blocks";
            case RING -> "Ring: " + format(attack.innerRadius()) + "–" + format(attack.size()) + " blocks";
            case CONE -> "Cone: " + format(attack.coneLength()) + " blocks · " + format(attack.angle()) + "°";
            case BEAM -> "Beam radius: " + format(attack.size()) + " blocks";
            case CHAIN -> "Chain: " + attack.chainTargets() + " targets · " + format(attack.size()) + "-block jumps";
            case DASH -> "Path radius: " + format(attack.size()) + " blocks";
            case TELEPORT -> "Blink distance: " + format(attack.size()) + " blocks";
            case SELF -> null;
        };
        if (detail != null)
            lore.add(detail);
        lore.add("Cast: " + modeLabel(attack.castMode())
                + (attack.castMode() == CastMode.DELAYED ? " (" + format(attack.delayTicks() / 20.0) + "s)" : "")
                + " · Cooldown: " + format(attack.cooldownTicks() / 20.0) + "s");
        if (attack.pulses() > 1)
            lore.add("Lingers: " + attack.pulses() + " pulses, every " + format(attack.pulseIntervalTicks() / 20.0)
                    + "s");
        if (attack.condition() != Condition.ALWAYS)
            lore.add("Trigger: " + conditionLabel(attack.condition()));
        String victim = effectList(attack, false);
        if (shape.hitsVictims())
            lore.add("Effects: " + (victim.isEmpty() ? "None" : victim));
        String caster = effectList(attack, true);
        if (!caster.isEmpty())
            lore.add("Caster: " + caster);
        return lore;
    }
    private static String effectList(FighterAttack attack, boolean caster) {
        return attack.effects().stream().filter(e -> (e.category() == EffectCategory.CASTER) == caster).sorted()
                .map(effect -> label(effect)
                        + (effect.casterBuff() || !caster && !effect.movement() && effect != Effect.FIRE
                                ? " " + roman(attack.effectLevel())
                                : ""))
                .collect(Collectors.joining(", "));
    }

    private static String label(Enum<?> value) {
        return java.util.Arrays.stream(value.name().toLowerCase(Locale.ROOT).split("_"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }
    static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001)
            return Long.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "");
    }
    static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(level);
        };
    }
    private static String hint(String action, String text) {
        return LegacyText.YELLOW + action + ": " + LegacyText.GRAY + text;
    }
    private static boolean isIconClick(InventoryClickEvent event) {
        return event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP;
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
        return switch (attack.shape()) {
            case TELEPORT, CHAIN, DASH, SELF -> shapeIcon(attack.shape());
            default -> AbilityVisuals.icon(attack.visual());
        };
    }
    private static Material shapeIcon(Shape shape) {
        return switch (shape) {
            case SPHERE -> Material.SLIME_BALL;
            case RING -> Material.TARGET;
            case CONE -> Material.HOPPER;
            case BEAM -> Material.END_ROD;
            case CHAIN -> Material.IRON_CHAIN;
            case DASH -> Material.TRIDENT;
            case TELEPORT -> Material.ENDER_PEARL;
            case SELF -> Material.NETHER_STAR;
        };
    }
    private static String shapeDescription(Shape shape) {
        return switch (shape) {
            case SPHERE -> "A round area around the caster or a marked target position";
            case RING -> "A hollow ring; standing close to the centre is safe";
            case CONE -> "A widening cone aimed at the opponent";
            case BEAM -> "A narrow beam from the caster that stops at blocks";
            case CHAIN -> "Strikes the opponent, then jumps between nearby enemies";
            case DASH -> "The caster rushes at the opponent, striking along its path";
            case TELEPORT -> "Moves the caster to a nearby safe landing spot";
            case SELF -> "Empowers the caster; only caster buffs apply";
        };
    }
    private static String rangeHelp(Shape shape) {
        return switch (shape) {
            case BEAM -> "Opponent must be this close; also the beam's length";
            case DASH -> "Opponent must be this close; also the furthest dash";
            case CHAIN -> "Opponent must be this close for the first strike";
            default -> "How close the opponent must be to start casting";
        };
    }
    private static String sizeHelp(Shape shape) {
        return switch (shape) {
            case CHAIN -> "How far the strike can jump between victims";
            case TELEPORT -> "Furthest distance the caster can blink";
            case DASH -> "How wide the path that hits victims is";
            default -> "Size of the affected area";
        };
    }
    private static List<String> shapeTips(Shape shape) {
        return switch (shape) {
            case SPHERE -> List.of("Caster spheres follow the NPC during the delay",
                    "Target spheres keep the opponent's marked position", "Can linger and pulse (Timing tab)");
            case RING -> List.of("Hits between the inner and outer radius",
                    "Punishes kiting; close combatants are safe", "Can linger and pulse (Timing tab)");
            case CONE -> List.of("Aim locks when casting starts", "Moving sideways dodges a delayed cone",
                    "Can linger and pulse, e.g. sustained breath");
            case BEAM -> List.of("Aim locks when casting starts; stops at blocks",
                    "Activation range is also the beam length", "Can linger and pulse, e.g. a searing ray");
            case CHAIN -> List.of("Needs sight of the first target", "Each jump needs sight from the previous victim",
                    "Jumps follow alliance and target rules");
            case DASH -> List.of("Stops in front of the target or a wall", "Fizzles without a safe landing spot",
                    "Set a Minimum Range so it closes distance");
            case TELEPORT -> List.of("Chooses a nearby safe landing spot",
                    "Add caster buffs (e.g. Speed) for an evasive escape");
            case SELF -> List.of("Applies caster buffs from the Effects tab",
                    "Pair with a Trigger like Caster below 50% health");
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
            case DELAYED -> "Pause and telegraph the area before releasing";
            case NEXT_ATTACK -> "Charge up; release on the next weapon hit (30s)";
        };
    }
    private static String conditionLabel(Condition condition) {
        return switch (condition) {
            case ALWAYS -> "Always";
            case CASTER_HURT -> "Caster below 50% health";
            case CASTER_CRITICAL -> "Caster below 25% health";
            case TARGET_WOUNDED -> "Target below 50% health";
        };
    }
    private static Material conditionIcon(Condition condition) {
        return switch (condition) {
            case ALWAYS -> Material.LIME_DYE;
            case CASTER_HURT -> Material.GLISTERING_MELON_SLICE;
            case CASTER_CRITICAL -> Material.FERMENTED_SPIDER_EYE;
            case TARGET_WOUNDED -> Material.SKELETON_SKULL;
        };
    }
    private static String conditionDescription(Condition condition) {
        return switch (condition) {
            case ALWAYS -> "Used whenever it is ready and in range";
            case CASTER_HURT -> "Saved for when the NPC is hurt";
            case CASTER_CRITICAL -> "A last resort when the NPC is nearly defeated";
            case TARGET_WOUNDED -> "A finisher once the opponent is weakened";
        };
    }
    private static ItemStack categoryHeader(EffectCategory category, FighterAttack attack) {
        return switch (category) {
            case AFFLICTION -> item(Material.RED_STAINED_GLASS_PANE, "Afflictions",
                    List.of("Harm victims over time", "Use Effect Duration and Effect Level"));
            case CONTROL -> item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "Crowd Control",
                    List.of("Hinder or move victims", "Knockback, Pull, and Launch use Force"));
            case CASTER -> item(Material.LIME_STAINED_GLASS_PANE, "Caster Buffs",
                    List.of("Apply to the NPC when released", "even if nothing is hit",
                            "Life Drain heals by damage actually dealt"));
        };
    }
    private static Material effectIcon(Effect effect) {
        return switch (effect) {
            case FIRE -> Material.FIRE_CHARGE;
            case POISON -> Material.POISONOUS_POTATO;
            case WITHER -> Material.WITHER_ROSE;
            case WEAKNESS -> Material.FERMENTED_SPIDER_EYE;
            case BLINDNESS -> Material.INK_SAC;
            case DARKNESS -> Material.SCULK_SENSOR;
            case NAUSEA -> Material.PUFFERFISH;
            case HUNGER -> Material.ROTTEN_FLESH;
            case SLOWNESS -> Material.COBWEB;
            case MINING_FATIGUE -> Material.PRISMARINE_SHARD;
            case LEVITATION -> Material.SHULKER_SHELL;
            case GLOWING -> Material.GLOWSTONE_DUST;
            case KNOCKBACK -> Material.PISTON;
            case PULL -> Material.LEAD;
            case LAUNCH -> Material.SLIME_BLOCK;
            case LIFE_DRAIN -> Material.GHAST_TEAR;
            case REGENERATION -> Material.GOLDEN_APPLE;
            case SPEED -> Material.SUGAR;
            case STRENGTH -> Material.BLAZE_ROD;
            case RESISTANCE -> Material.SHIELD;
            case ABSORPTION -> Material.GOLDEN_CARROT;
        };
    }
    private static String effectDescription(Effect effect) {
        return switch (effect) {
            case FIRE -> "Sets victims on fire";
            case POISON -> "Damage over time; cannot kill";
            case WITHER -> "Damage over time; can kill";
            case WEAKNESS -> "Reduces melee damage, including NPCs'";
            case BLINDNESS -> "Limits vision and prevents sprinting";
            case DARKNESS -> "Pulsing darkness that limits vision";
            case NAUSEA -> "Distorts the victim's screen";
            case HUNGER -> "Drains food";
            case SLOWNESS -> "Slows movement, including NPC navigation";
            case MINING_FATIGUE -> "Slows attacks and mining";
            case LEVITATION -> "Floats victims upwards";
            case GLOWING -> "Outlines victims through walls";
            case KNOCKBACK -> "Pushes victims away from the caster";
            case PULL -> "Draws victims towards the area's centre";
            case LAUNCH -> "Throws victims into the air";
            case LIFE_DRAIN -> "Heals the caster by damage dealt";
            case REGENERATION -> "Regenerates the caster's health";
            case SPEED -> "Makes the caster faster";
            case STRENGTH -> "Increases the caster's melee damage";
            case RESISTANCE -> "Reduces damage the caster takes";
            case ABSORPTION -> "Grants the caster extra absorption hearts";
        };
    }

    private static ItemStack glint(ItemStack item) {
        var meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
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

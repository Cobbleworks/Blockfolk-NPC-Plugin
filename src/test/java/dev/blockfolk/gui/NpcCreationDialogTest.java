package dev.blockfolk.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.blockfolk.gui.NpcCreationDialog.CombatChoice;
import dev.blockfolk.model.AttackReaction;
import dev.blockfolk.model.CombatProfile;
import io.papermc.paper.dialog.DialogResponseView;
import net.kyori.adventure.nbt.api.BinaryTagHolder;

class NpcCreationDialogTest {

    @Test
    void readsAllCreationProperties() {
        NpcCreationDialog.CreationValues values = NpcCreationDialog.CreationValues.from(response(Map.of()));

        assertEquals(new NpcCreationDialog.CreationValues("Guard", "https://example.com/skin.png", 35, 40,
                CombatChoice.GUARD, 120, true, false, true, false), values);
    }

    @Test
    void rejectsMissingOrInvalidSliderValues() {
        assertNull(read("health", null));
        assertNull(read("health", Float.NaN));
        assertNull(read("health", 10.5F));
        assertNull(read("health", 201.0F));
        assertNull(read("experience", 201.0F));
        assertNull(read("experience", -1.0F));
        assertNull(read("respawn", -1.0F));
        assertNull(read("respawn", 3601.0F));
    }

    @Test
    void acceptsSliderMaximums() {
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("health", 200.0F);
        overrides.put("experience", 200.0F);
        NpcCreationDialog.CreationValues values = NpcCreationDialog.CreationValues.from(response(overrides));

        assertEquals(200, values.health());
        assertEquals(200, values.experience());
    }

    @Test
    void rejectsMissingCheckboxUnknownCombatAndOversizedText() {
        assertNull(read("pickup", null));
        assertNull(read("combat", "berserk"));
        assertNull(read("combat", null));
        assertNull(read("name", "x".repeat(65)));
        assertNull(read("skin", "x".repeat(513)));
        assertNull(read("skin", null));
    }

    @Test
    void combatChoicesMapToAttackReactions() {
        CombatProfile base = CombatProfile.disabled();

        assertEquals(AttackReaction.IGNORE, CombatChoice.IGNORE.applyTo(base).attackReaction());
        assertEquals(AttackReaction.FIGHT_BACK, CombatChoice.FIGHT_BACK.applyTo(base).attackReaction());
        assertEquals(AttackReaction.FLEE, CombatChoice.FLEE.applyTo(base).attackReaction());
        CombatProfile guard = CombatChoice.GUARD.applyTo(base);
        assertEquals(AttackReaction.HUNTING, guard.attackReaction());
        assertEquals(true, guard.targetMobs());
        assertEquals(false, guard.targetPlayers());
    }

    private static NpcCreationDialog.CreationValues read(String key, Object value) {
        Map<String, Object> overrides = new HashMap<>();
        overrides.put(key, value);
        return NpcCreationDialog.CreationValues.from(response(overrides));
    }

    private static DialogResponseView response(Map<String, Object> overrides) {
        Map<String, Object> values = new HashMap<>(Map.of("name", "Guard", "skin", "https://example.com/skin.png",
                "health", 35.0F, "experience", 40.0F, "combat", "guard", "respawn", 120.0F, "pickup", true, "show_name",
                false, "look_at_player", true, "pushable", false));
        values.putAll(overrides);
        return new DialogResponseView() {
            @Override
            public BinaryTagHolder payload() {
                return null;
            }

            @Override
            public String getText(String key) {
                return values.get(key) instanceof String text ? text : null;
            }

            @Override
            public Boolean getBoolean(String key) {
                return values.get(key) instanceof Boolean bool ? bool : null;
            }

            @Override
            public Float getFloat(String key) {
                return values.get(key) instanceof Float number ? number : null;
            }
        };
    }
}

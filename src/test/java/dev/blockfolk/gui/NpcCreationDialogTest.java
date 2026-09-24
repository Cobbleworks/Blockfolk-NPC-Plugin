package dev.blockfolk.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import io.papermc.paper.dialog.DialogResponseView;
import net.kyori.adventure.nbt.api.BinaryTagHolder;

class NpcCreationDialogTest {

    @Test
    void readsAllCreationProperties() {
        NpcCreationDialog.CreationValues values = NpcCreationDialog.CreationValues
                .from(response("Guard", 35.0F, 120.0F, true));

        assertEquals(new NpcCreationDialog.CreationValues("Guard", 35, 120, true, false, true, false), values);
    }

    @Test
    void rejectsMissingOrInvalidSliderValues() {
        assertNull(NpcCreationDialog.CreationValues.from(response("Guard", null, 0.0F, true)));
        assertNull(NpcCreationDialog.CreationValues.from(response("Guard", Float.NaN, 0.0F, true)));
        assertNull(NpcCreationDialog.CreationValues.from(response("Guard", 10.5F, 0.0F, true)));
        assertNull(NpcCreationDialog.CreationValues.from(response("Guard", 1025.0F, 0.0F, true)));
        assertNull(NpcCreationDialog.CreationValues.from(response("Guard", 20.0F, -1.0F, true)));
        assertNull(NpcCreationDialog.CreationValues.from(response("Guard", 20.0F, 3601.0F, true)));
    }

    @Test
    void rejectsMissingCheckboxAndOversizedName() {
        assertNull(NpcCreationDialog.CreationValues.from(response("Guard", 20.0F, 0.0F, null)));
        assertNull(NpcCreationDialog.CreationValues.from(response("x".repeat(65), 20.0F, 0.0F, true)));
    }

    private DialogResponseView response(String name, Float health, Float respawn, Boolean checkbox) {
        return new DialogResponseView() {
            @Override
            public BinaryTagHolder payload() {
                return null;
            }

            @Override
            public String getText(String key) {
                return name;
            }

            @Override
            public Boolean getBoolean(String key) {
                return switch (key) {
                    case "pickup" -> checkbox;
                    case "show_name", "pushable" -> false;
                    default -> true;
                };
            }

            @Override
            public Float getFloat(String key) {
                return "health".equals(key) ? health : respawn;
            }
        };
    }
}

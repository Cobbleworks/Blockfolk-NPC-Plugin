package dev.blockfolk.integration.beautyquests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class BlockfolkMobFactoryTest {

    private final BlockfolkMobFactory factory = new BlockfolkMobFactory(null, null, null);

    @Test
    void storesPresetKeyAsQuestValue() {
        BlockfolkMobFactory.Target target = factory.fromValue("donkey-kong");
        assertEquals("donkey-kong", target.definitionKey());
        assertEquals("donkey-kong", factory.getValue(target));
    }

    @Test
    void keepsTargetsWhosePresetIsMissing() {
        // Quests must still load after a preset is deleted.
        assertEquals("deleted-preset", factory.fromValue("deleted-preset").definitionKey());
    }

    @Test
    void rejectsBlankValues() {
        assertNull(factory.fromValue(""));
        assertNull(factory.fromValue(null));
    }
}

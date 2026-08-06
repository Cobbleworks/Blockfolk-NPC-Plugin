package dev.blockfolk.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class LocationCodecTest {

    @Test
    void retainsWorldNameWithoutRequiringTheWorldToBeLoaded() {
        YamlConfiguration yaml = new YamlConfiguration();
        var section = yaml.createSection("location");
        section.set("world", "custom_world");
        section.set("x", 1.5);
        section.set("y", 64.0);
        section.set("z", -2.5);

        var stored = LocationCodec.readStored(section);

        assertEquals("custom_world", stored.worldName());
        assertEquals(1.5, stored.x());
        assertNull(stored.toLocation().getWorld());
    }
}

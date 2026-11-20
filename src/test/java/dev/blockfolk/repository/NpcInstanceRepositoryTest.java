package dev.blockfolk.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.model.StoredLocation;

class NpcInstanceRepositoryTest {

    private static NpcInstance instance() {
        StoredLocation location = new StoredLocation("world", 1, 2, 3, 0, 0);
        return new NpcInstance(UUID.randomUUID(), "guard", location, location, 0L);
    }

    @Test
    void plainInstancesWriteNoVariantKeys() {
        YamlConfiguration yaml = new YamlConfiguration();
        NpcInstanceRepository.writeOverrides(yaml, instance());

        assertTrue(yaml.getKeys(false).isEmpty());
    }

    @Test
    void variantOverridesSurviveARoundTrip() throws Exception {
        NpcInstance saved = instance();
        saved.setLabel("North Gate");
        saved.setResolvedSkin("https://example.com/north.png", "value", "signature");
        saved.setRouteSwap("patrol", "patrol-north");
        saved.setRouteSwap("guards/market", "guards/market-east");
        YamlConfiguration written = new YamlConfiguration();
        NpcInstanceRepository.writeOverrides(written, saved);

        YamlConfiguration read = new YamlConfiguration();
        read.loadFromString(written.saveToString());
        NpcInstance loaded = instance();
        NpcInstanceRepository.readOverrides(read, loaded);

        assertEquals("North Gate", loaded.getLabel());
        assertEquals("https://example.com/north.png", loaded.getSkinUrl());
        assertEquals("value", loaded.getSkinTextureValue());
        assertEquals("signature", loaded.getSkinTextureSignature());
        assertEquals(Map.of("patrol", "patrol-north", "guards/market", "guards/market-east"), loaded.getRouteSwaps());
    }

    @Test
    void oldInstanceEntriesLoadWithoutOverrides() throws Exception {
        YamlConfiguration old = new YamlConfiguration();
        old.loadFromString("definition: guard\nlocation:\n  world: world\n  x: 1.0\n  y: 2.0\n  z: 3.0\n");
        NpcInstance loaded = instance();
        NpcInstanceRepository.readOverrides(old, loaded);

        assertFalse(loaded.hasOverrides());
        assertNull(loaded.getSkinUrl());
    }

    @Test
    void malformedRouteSwapsAreSkipped() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("route-swaps:\n  patrol: patrol-north\n  broken: ''\n");
        NpcInstance loaded = instance();
        NpcInstanceRepository.readOverrides(yaml, loaded);

        assertEquals(Map.of("patrol", "patrol-north"), loaded.getRouteSwaps());
    }

    @Test
    void skinSectionIsWrittenAsOneBlock() {
        NpcInstance saved = instance();
        saved.setSkinUrl("https://textures.minecraft.net/texture/abc");
        YamlConfiguration yaml = new YamlConfiguration();
        NpcInstanceRepository.writeOverrides(yaml, saved);

        ConfigurationSection skin = yaml.getConfigurationSection("skin");
        assertEquals("https://textures.minecraft.net/texture/abc", skin.getString("url"));
        assertFalse(skin.contains("value"));
    }
}

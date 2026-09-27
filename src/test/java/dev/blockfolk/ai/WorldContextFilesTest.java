package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldContextFilesTest {

    @TempDir
    Path dataFolder;

    @Test
    void readsOnlyTheMatchingWorldFileAndPicksUpEdits() throws IOException {
        WorldContextFiles files = new WorldContextFiles(dataFolder, Logger.getAnonymousLogger());
        Files.writeString(dataFolder.resolve("world.md"), "  Village market at spawn.\n");
        Files.writeString(dataFolder.resolve("world_nether.md"), "Fortress to the east.");

        assertEquals("Village market at spawn.", files.read("world"));
        assertEquals("Fortress to the east.", files.read("world_nether"));
        assertEquals("", files.read("customworld"));

        Files.writeString(dataFolder.resolve("world.md"), "The market moved.");
        assertEquals("The market moved.", files.read("world"));
    }

    @Test
    void rejectsPathsOutsideTheDataFolder() throws IOException {
        Path pluginFolder = Files.createDirectory(dataFolder.resolve("plugin"));
        WorldContextFiles files = new WorldContextFiles(pluginFolder, Logger.getAnonymousLogger());
        Files.writeString(dataFolder.resolve("elsewhere.md"), "Outside data folder");

        assertEquals("", files.read("../elsewhere"));
    }
}

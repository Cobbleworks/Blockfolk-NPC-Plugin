package dev.blockfolk.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Reads optional, world-specific prompt context from the plugin data folder.
 */
final class WorldContextFiles {

    private final Path dataFolder;
    private final Logger logger;

    WorldContextFiles(Path dataFolder, Logger logger) {
        this.dataFolder = dataFolder.toAbsolutePath().normalize();
        this.logger = logger;
    }

    String read(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return "";
        }
        Path file = dataFolder.resolve(worldName + ".md").normalize();
        if (!dataFolder.equals(file.getParent()) || !Files.exists(file)) {
            return "";
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8).trim();
        } catch (IOException error) {
            logger.warning("Could not read world context file " + file + ": " + error.getMessage());
            return "";
        }
    }
}

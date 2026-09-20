package com.safeanchor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads and saves {@link SafeAnchorConfig} as JSON in the Fabric config dir.
 */
public final class SafeAnchorConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "safeanchor.json";

    private static SafeAnchorConfig config;

    private SafeAnchorConfigManager() {
    }

    /**
     * @return the active config, loading it from disk on first access.
     */
    public static synchronized SafeAnchorConfig get() {
        if (config == null) {
            load();
        }
        return config;
    }

    /**
     * Loads the config from disk, creating defaults when missing or corrupt.
     */
    public static synchronized void load() {
        Path file = configFile();
        SafeAnchorConfig loaded = null;

        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(reader, SafeAnchorConfig.class);
            } catch (IOException | JsonSyntaxException e) {
                SafeAnchorLogger.warn("Config is corrupt (" + e.getMessage() + "), backing it up and using defaults.");
                backupCorruptFile(file);
            }
        }

        if (loaded == null) {
            loaded = new SafeAnchorConfig();
        }

        boolean repaired;
        try {
            repaired = loaded.validateAndRepair();
        } catch (Exception e) {
            SafeAnchorLogger.warn("Config validation failed, using defaults: " + e.getMessage());
            loaded = new SafeAnchorConfig();
            repaired = true;
        }

        config = loaded;
        if (repaired || Files.notExists(file)) {
            save();
        }
    }

    /**
     * Persists the active config to disk.
     */
    public static synchronized void save() {
        if (config == null) {
            config = new SafeAnchorConfig();
        }
        Path file = configFile();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            SafeAnchorLogger.error("Could not save config: " + e.getMessage());
        }
    }

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static void backupCorruptFile(Path file) {
        try {
            Path backup = file.resolveSibling(FILE_NAME + ".bak");
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            SafeAnchorLogger.error("Could not back up corrupt config: " + e.getMessage());
        }
    }
}

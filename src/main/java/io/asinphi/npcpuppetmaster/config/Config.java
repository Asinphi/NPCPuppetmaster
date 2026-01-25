package io.asinphi.npcpuppetmaster.config;

import io.asinphi.npcpuppetmaster.NPCPuppetmaster;
import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Config {

    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir()
                    .resolve("NPCPuppetmaster")
                    .resolve("config.yml");

    // Cached parsed YAML root (populated by reload()).
    private static volatile Map<?, ?> yamlRoot = Map.of();

    private static boolean areNpcsPuppetsByDefault;
    private static List<String> possessionCommands;
    private static List<String> unpossessionCommands;
    private static boolean isDebugEnabled;
    private static int configVersion;

    public static boolean areNpcsPuppetsByDefault () {
        return areNpcsPuppetsByDefault;
    }

    public static List<String> getPossessionCommands() {
        return possessionCommands;
    }

    public static List<String> getUnpossessionCommands() {
        return unpossessionCommands;
    }

    public static boolean isDebugEnabled() {
        return isDebugEnabled;
    }

    public static int configVersion() {
        return configVersion;
    }

    public static void initValues() {
        Map<?, ?> map = yamlRoot;

        areNpcsPuppetsByDefault = Boolean.parseBoolean(map.get("npcs_are_puppets_by_default").toString());
        possessionCommands = requireStringList(map, "possession_commands");
        unpossessionCommands = requireStringList(map, "unpossession_commands");
        isDebugEnabled = Boolean.parseBoolean(map.get("enable_debug").toString());
        configVersion = requireInt(map, "config_version");
    }

    public static void load() {
        reload();
    }

    /**
     * Reloads config.yml from disk and caches the parsed YAML root.
     * Does NOT apply any defaults in code; the bundled config.yml is authoritative.
     */
    public static void reload() {
        ensureDefaults();
        yamlRoot = loadYamlRoot();
        initValues();
    }

    private static Map<?, ?> loadYamlRoot() {
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            Object data = new Yaml().load(reader);
            if (!(data instanceof Map<?, ?> map)) {
                throw new IllegalStateException("config.yml root must be a YAML map/object.");
            }
            return map;
        } catch (Exception e) {
            NPCPuppetmaster.LOGGER.error("[Config] Failed to read config.yml: {}", CONFIG_PATH, e);
            throw new RuntimeException("Failed to read config.yml: " + CONFIG_PATH, e);
        }
    }

    private static int requireInt(Map<?, ?> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            throw new IllegalStateException("Missing required config key: " + key);
        }
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new IllegalStateException("Config key '" + key + "' must be an integer (got " + v + ")");
    }

    private static List<String> requireStringList(Map<?, ?> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            throw new IllegalStateException("Missing required config key: " + key);
        }
        List<String> result = new ArrayList<>();
        if (v instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof String)) {
                    throw new IllegalStateException("Config key '" + key + "' must be a list of strings (got non-string item: " + item + ")");
                }
                result.add((String) item);
            }
            return result;
        }
        throw new IllegalStateException("Config key '" + key + "' must be a list of strings (got " + v + ")");
    }

    private static void ensureDefaults() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.exists(CONFIG_PATH)) return;

            // src/main/resources/assets/<modid>/config.yml
            String resourcePath = "assets/" + NPCPuppetmaster.MOD_ID + "/config.yml";

            try (var in = Config.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) {
                    throw new IllegalStateException("Missing bundled config.yml at: " + resourcePath);
                }
                Files.copy(in, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }

            NPCPuppetmaster.LOGGER.info("[Config] Created default config.yml at {}", CONFIG_PATH);

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize config.yml", e);
        }
    }

    private Config() {}
}

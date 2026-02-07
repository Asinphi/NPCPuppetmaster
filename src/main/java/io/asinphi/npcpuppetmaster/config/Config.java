package io.asinphi.npcpuppetmaster.config;

import io.asinphi.npcpuppetmaster.NPCPuppetmaster;
import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
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
    private static boolean areNpcsOwnerOnlyByDefault;
    private static PresetOptions defaultPresetOptions;
    private static Map<String, PresetOptions> puppetPresets;
    private static PresetOptions familiarTemplatePreset;
    private static int createPuppetMaxCount;
    private static long createPuppetWindowMillis;
    private static int maxFamiliarTotal;
    private static int maxFamiliarSpawned;
    private static List<String> possessionCommands;
    private static List<String> unpossessionCommands;
    private static boolean isDebugEnabled;
    private static int configVersion;

    public static boolean areNpcsPuppetsByDefault () {
        return areNpcsPuppetsByDefault;
    }

    public static boolean areNpcsOwnerOnlyByDefault () {
        return areNpcsOwnerOnlyByDefault;
    }

    public static Map<String, PresetOptions> getPuppetPresets() {
        return puppetPresets;
    }

    public static PresetOptions getFamiliarTemplatePreset() {
        return familiarTemplatePreset;
    }

    public static PresetOptions getDefaultPresetOptions() {
        return defaultPresetOptions;
    }

    public static int getCreatePuppetMaxCount() {
        return createPuppetMaxCount;
    }

    public static long getCreatePuppetWindowMillis() {
        return createPuppetWindowMillis;
    }

    public static int getMaxFamiliarTotal() {
        return maxFamiliarTotal;
    }

    public static int getMaxFamiliarSpawned() {
        return maxFamiliarSpawned;
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

        areNpcsPuppetsByDefault = requireBoolean(map, "npcs_are_puppets_by_default");
        areNpcsOwnerOnlyByDefault = requireBoolean(map, "npcs_are_owner_only_by_default");
        defaultPresetOptions = loadDefaultPresetOptions(map);
        puppetPresets = loadPuppetPresets(map, defaultPresetOptions);
        familiarTemplatePreset = loadSinglePreset(map, "familiar_template_preset", defaultPresetOptions);
        createPuppetMaxCount = requireInt((Map<?, ?>) map.get("create_puppet_rate_limit"), "max_puppets");
        createPuppetWindowMillis = requireInt((Map<?, ?>) map.get("create_puppet_rate_limit"),
                "interval_seconds") * 1000L;
        Map<?, ?> familiarSlots = requireMap(map, "max_familiar_slots");
        maxFamiliarTotal = requireInt(familiarSlots, "total");
        maxFamiliarSpawned = requireInt(familiarSlots, "spawned");
        possessionCommands = requireStringList(map, "possession_commands");
        unpossessionCommands = requireStringList(map, "unpossession_commands");
        isDebugEnabled = requireBoolean(map, "enable_debug");
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

    private static boolean requireBoolean(Map<?, ?> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            throw new IllegalStateException("Missing required config key: " + key);
        }
        return parseBoolean(key, v);
    }

    private static Boolean optionalBoolean(Map<?, ?> map, String key) {
        if (!map.containsKey(key)) {
            return null;
        }
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        return parseBoolean(key, v);
    }

    private static boolean parseBoolean(String key, Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) {
            String normalized = s.trim().toLowerCase();
            if ("true".equals(normalized)) return true;
            if ("false".equals(normalized)) return false;
        }
        throw new IllegalStateException("Config key '" + key + "' must be a boolean (got " + v + ")");
    }

    private static String requireString(Map<?, ?> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            throw new IllegalStateException("Missing required config key: " + key);
        }
        if (v instanceof String s) return s;
        throw new IllegalStateException("Config key '" + key + "' must be a string (got " + v + ")");
    }

    private static Map<?, ?> requireMap(Map<?, ?> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            throw new IllegalStateException("Missing required config key: " + key);
        }
        if (v instanceof Map<?, ?> rawMap) {
            return rawMap;
        }
        throw new IllegalStateException("Config key '" + key + "' must be a map (got " + v + ")");
    }

    private static <K, V> Map<K, V> requireMap(Map<?, ?> map, String key, Class<K> keyClass, Class<V> valueClass) {
        Object v = map.get(key);
        if (v == null) {
            throw new IllegalStateException("Missing required config key: " + key);
        }
        Map<K, V> result = new HashMap<>();
        if (v instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (!keyClass.isInstance(entry.getKey()) || !valueClass.isInstance(entry.getValue())) {
                    throw new IllegalStateException("Config key '" + key + "' must be a map of " +
                            keyClass.getSimpleName() + " to " + valueClass.getSimpleName() +
                            " (got invalid entry: " + entry + ")");
                }
                result.put(keyClass.cast(entry.getKey()), valueClass.cast(entry.getValue()));
            }
            return result;
        }
        throw new IllegalStateException("Config key '" + key + "' must be a map (got " + v + ")");
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

    private static PresetOptions loadDefaultPresetOptions(Map<?, ?> map) {
        Map<?, ?> defaults = requireMap(map, "default_preset_options");
        return PresetOptions.fromOptions(defaults, null);
    }

    private static Map<String, PresetOptions> loadPuppetPresets(Map<?, ?> map, PresetOptions defaults) {
        Map<?, ?> presetRoot = requireMap(map, "puppet_presets");
        Map<String, PresetOptions> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : presetRoot.entrySet()) {
            if (!(entry.getKey() instanceof String presetName)) {
                throw new IllegalStateException("puppet_presets keys must be strings (got " + entry.getKey() + ")");
            }
            PresetOptions presetOptions = parsePresetOptions("puppet_presets." + presetName, entry.getValue());
            result.put(presetName, defaults.merge(presetOptions).requireComplete(presetName));
        }
        return result;
    }

    private static PresetOptions loadSinglePreset(Map<?, ?> map, String key, PresetOptions defaults) {
        Object raw = map.get(key);
        if (raw == null) {
            throw new IllegalStateException("Missing required config key: " + key);
        }
        PresetOptions presetOptions = parsePresetOptions(key, raw);
        return defaults.merge(presetOptions).requireComplete(key);
    }

    private static PresetOptions parsePresetOptions(String context, Object rawPreset) {
        if (rawPreset instanceof String rawPath) {
            return new PresetOptions(rawPath, null, null, null);
        }
        if (rawPreset instanceof Map<?, ?> presetMap) {
            String path = requireString(presetMap, "path");
            Map<?, ?> optionsMap = presetMap;
            Object optionsRaw = presetMap.get("options");
            if (optionsRaw != null) {
                if (optionsRaw instanceof Map<?, ?> nestedOptions) {
                    optionsMap = nestedOptions;
                } else {
                    throw new IllegalStateException("Config key 'options' in '" + context + "' must be a map (got " + optionsRaw + ")");
                }
            }
            return PresetOptions.fromOptions(optionsMap, path);
        }
        throw new IllegalStateException("Config key '" + context + "' must be a string path or map (got " + rawPreset + ")");
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

    public static final class PresetOptions {
        private final String path;
        private final Boolean isPuppet;
        private final Boolean isOwnerOnly;
        private final Boolean isTransient;

        public PresetOptions(String path, Boolean isPuppet, Boolean isOwnerOnly, Boolean isTransient) {
            this.path = path;
            this.isPuppet = isPuppet;
            this.isOwnerOnly = isOwnerOnly;
            this.isTransient = isTransient;
        }

        public String getPath() {
            return path;
        }

        public boolean isPuppet() {
            return Boolean.TRUE.equals(isPuppet);
        }

        public boolean isOwnerOnly() {
            return Boolean.TRUE.equals(isOwnerOnly);
        }

        public boolean isTransient() {
            return Boolean.TRUE.equals(isTransient);
        }

        public PresetOptions merge(PresetOptions overrides) {
            return new PresetOptions(
                    overrides.path != null ? overrides.path : this.path,
                    overrides.isPuppet != null ? overrides.isPuppet : this.isPuppet,
                    overrides.isOwnerOnly != null ? overrides.isOwnerOnly : this.isOwnerOnly,
                    overrides.isTransient != null ? overrides.isTransient : this.isTransient
            );
        }

        public PresetOptions requireComplete(String presetName) {
            if (path == null || path.isBlank()) {
                throw new IllegalStateException("Preset '" + presetName + "' is missing required path.");
            }
            if (isPuppet == null) {
                throw new IllegalStateException("Preset '" + presetName + "' is missing required option: is_puppet");
            }
            if (isOwnerOnly == null) {
                throw new IllegalStateException("Preset '" + presetName + "' is missing required option: owner_only");
            }
            if (isTransient == null) {
                throw new IllegalStateException("Preset '" + presetName + "' is missing required option: transient");
            }
            return this;
        }

        private static PresetOptions fromOptions(Map<?, ?> options, String path) {
            return new PresetOptions(
                    path,
                    optionalBoolean(options, "is_puppet"),
                    optionalBoolean(options, "owner_only"),
                    optionalBoolean(options, "transient")
            );
        }
    }
}

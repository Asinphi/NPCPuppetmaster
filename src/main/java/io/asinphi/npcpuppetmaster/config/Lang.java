package io.asinphi.npcpuppetmaster.config;

import io.asinphi.npcpuppetmaster.NPCPuppetmaster;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.kyori.adventure.platform.fabric.FabricServerAudiences;
import net.kyori.adventure.platform.fabric.PlayerLocales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Lang {
    private static final String DEFAULT_LOCALE = "en_us";
    private static final String GLOBAL_PLACEHOLDERS_ROOT = "global_placeholders";
    private static final Path LANG_DIR =
            FabricLoader.getInstance().getConfigDir().resolve("NPCPuppetmaster").resolve("lang");
    private static final List<String> BUNDLED_LOCALES = List.of(
            "en_us"
    );
    private static final int MAX_MISSING_KEYS_LOGGED = 8;

    private static final MiniMessage MM = MiniMessage.builder()
            .tags(TagResolver.builder().resolver(StandardTags.defaults()).build())
            .build();

    private static volatile FabricServerAudiences adventure;

    // Player locale tracking (server-side)
    private static final Map<UUID, Locale> playerLocales = new ConcurrentHashMap<>();

    // localeTag -> (messageKey -> raw string)
    private static final Map<String, Map<String, String>> bundles = new ConcurrentHashMap<>();

    // localeTag -> (globalKey (can contain dots) -> raw string)
    private static final Map<String, Map<String, String>> globals = new ConcurrentHashMap<>();

    // Prevent infinite recursion
    private static final int GLOBAL_EXPANSION_MAX_PASSES = 10;

    public static void init() {
        ensureDefaults();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            adventure = FabricServerAudiences.of(server);

            // Preload default bundle
            loadLocale(DEFAULT_LOCALE);

            PlayerLocales.CHANGED_EVENT.register((player, locale) -> {
                if (locale == null) return;
                playerLocales.put(player.getUUID(), locale);

                // Warm cache for that locale if we haven't seen it before.
                String tag = toLocaleTag(locale);
                if (!bundles.containsKey(tag) || !globals.containsKey(tag)) {
                    loadLocale(tag);
                }
            });
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            var a = adventure;
            adventure = null;
            playerLocales.clear();
            bundles.clear();
            globals.clear();
            if (a != null) a.close();
        });
    }

    public static void reload() {
        ensureDefaults();
        Set<String> tags = new HashSet<>(bundles.keySet());
        tags.addAll(globals.keySet());
        tags.add(DEFAULT_LOCALE);

        for (Locale loc : playerLocales.values()) {
            tags.add(toLocaleTag(loc));
        }

        for (String tag : tags) {
            loadLocale(tag);
        }

        checkCompleteness(tags);
    }

    public static void send(ServerPlayer player, String key, TagResolver... placeholders) {
        adventure().player(player.getUUID()).sendMessage(render(player, key, placeholders));
    }

    public static void send(CommandSourceStack source, String key, TagResolver... placeholders) {
        var entity = source.getEntity();
        if (entity instanceof ServerPlayer player) {
            send(player, key, placeholders);
            return;
        }

        // Console / command block / function: default locale
        adventure().audience(source).sendMessage(render(DEFAULT_LOCALE, key, placeholders));
    }

    public static void sendSuccess(ServerPlayer player, String key, TagResolver... placeholders) {
        sendWithPrefix(player, "prefixes.success", key, placeholders);
    }

    public static void sendError(ServerPlayer player, String key, TagResolver... placeholders) {
        sendWithPrefix(player, "prefixes.error", key, placeholders);
    }

    public static void sendSuccess(CommandSourceStack source, String key, TagResolver... placeholders) {
        sendWithPrefix(source, "prefixes.success", key, placeholders);
    }

    public static void sendError(CommandSourceStack source, String key, TagResolver... placeholders) {
        sendWithPrefix(source, "prefixes.error", key, placeholders);
    }

    public static Component render(ServerPlayer player, String key, TagResolver... placeholders) {
        return render(localeTagFor(player), key, placeholders);
    }

    public static Component render(String localeTag, String key, TagResolver... placeholders) {
        localeTag = normalizeLocale(localeTag);

        String raw = getMessageRaw(localeTag, key);
        if (raw == null) {
            return Component.text("Missing lang key: " + key + " (locale=" + localeTag + ")");
        }

        // Expand <global.key> tokens BEFORE MiniMessage parsing
        raw = expandGlobals(localeTag, raw);

        try {
            return MM.deserialize(raw, TagResolver.resolver(placeholders));
        } catch (Exception e) {
            NPCPuppetmaster.LOGGER.error("[Lang] Failed to parse MiniMessage. locale={}, key={}, raw={}",
                    localeTag, key, raw, e);
            return Component.text(stripAngleBrackets(raw));
        }
    }

    private static void sendWithPrefix(ServerPlayer player, String prefixKey, String key, TagResolver... placeholders) {
        String localeTag = localeTagFor(player);

        Component prefix = renderGlobal(localeTag, prefixKey);
        Component body = render(localeTag, key, placeholders);

        adventure().player(player.getUUID()).sendMessage(prefix.append(body));
    }

    private static void sendWithPrefix(CommandSourceStack source, String prefixKey, String key, TagResolver... placeholders) {
        var entity = source.getEntity();
        if (entity instanceof ServerPlayer player) {
            sendWithPrefix(player, prefixKey, key, placeholders);
            return;
        }

        String localeTag = DEFAULT_LOCALE;

        Component prefix = renderGlobal(localeTag, prefixKey);
        Component body = render(localeTag, key, placeholders);

        adventure().audience(source).sendMessage(prefix.append(body));
    }

    private static Component renderGlobal(String localeTag, String globalKey) {
        localeTag = normalizeLocale(localeTag);

        String raw = getGlobalRaw(localeTag, globalKey);
        if (raw == null) return Component.empty();

        raw = expandGlobals(localeTag, raw);

        try {
            return MM.deserialize(raw);
        } catch (Exception e) {
            NPCPuppetmaster.LOGGER.error("[Lang] Failed to parse global placeholder. locale={}, key={}, raw={}",
                    localeTag, globalKey, raw, e);
            return Component.empty();
        }
    }

    private static FabricServerAudiences adventure() {
        var a = adventure;
        if (a == null) throw new IllegalStateException("Attempted to access Adventure without a running server!");
        return a;
    }

    private static String localeTagFor(ServerPlayer player) {
        Locale loc = playerLocales.get(player.getUUID());
        return loc == null ? DEFAULT_LOCALE : normalizeLocale(toLocaleTag(loc));
    }

    private static String normalizeLocale(String localeTag) {
        if (localeTag == null || localeTag.isBlank()) return DEFAULT_LOCALE;
        return localeTag.toLowerCase(Locale.ROOT);
    }

    /**
     * Convert Locale to a Minecraft-ish tag: en_us, pt_br, zh_cn, etc.
     * Locale might be "en_US" -> "en_us"
     */
    private static String toLocaleTag(Locale locale) {
        String lang = locale.getLanguage();
        String country = locale.getCountry();
        if (lang.isBlank()) return DEFAULT_LOCALE;
        if (country.isBlank()) return lang.toLowerCase(Locale.ROOT);
        return (lang + "_" + country).toLowerCase(Locale.ROOT);
    }

    private static String getMessageRaw(String localeTag, String key) {
        Map<String, String> bundle = bundles.get(localeTag);
        if (bundle == null) bundle = loadLocale(localeTag);

        String v = bundle.get(key);
        if (v != null) return v;

        if (!DEFAULT_LOCALE.equals(localeTag)) {
            Map<String, String> def = bundles.get(DEFAULT_LOCALE);
            if (def == null) def = loadLocale(DEFAULT_LOCALE);
            return def.get(key);
        }

        return null;
    }

    private static String getGlobalRaw(String localeTag, String key) {
        Map<String, String> g = globals.get(localeTag);
        if (g == null) {
            loadLocale(localeTag);
            g = globals.get(localeTag);
        }

        String v = g != null ? g.get(key) : null;
        if (v != null) return v;

        if (!DEFAULT_LOCALE.equals(localeTag)) {
            Map<String, String> def = globals.get(DEFAULT_LOCALE);
            if (def == null) {
                loadLocale(DEFAULT_LOCALE);
                def = globals.get(DEFAULT_LOCALE);
            }
            return def != null ? def.get(key) : null;
        }

        return null;
    }

    /**
     * Expand <some.global.key> tokens if that exact key exists in globals.
     * Leaves normal MiniMessage tags like <red> untouched because they won't exist in globals.
     */
    private static String expandGlobals(String localeTag, String raw) {
        if (raw == null || raw.isEmpty()) return raw;

        // Ensure loaded
        if (!globals.containsKey(localeTag)) loadLocale(localeTag);
        if (!globals.containsKey(DEFAULT_LOCALE)) loadLocale(DEFAULT_LOCALE);

        Map<String, String> loc = globals.getOrDefault(localeTag, Map.of());
        Map<String, String> def = globals.getOrDefault(DEFAULT_LOCALE, Map.of());

        String out = raw;
        for (int pass = 0; pass < GLOBAL_EXPANSION_MAX_PASSES; pass++) {
            String before = out;
            out = replaceGlobalTokensOnce(out, loc, def);
            if (out.equals(before)) break;
        }
        return out;
    }

    private static String replaceGlobalTokensOnce(String input, Map<String, String> loc, Map<String, String> def) {
        StringBuilder sb = new StringBuilder(input.length());
        int i = 0;

        while (i < input.length()) {
            int lt = input.indexOf('<', i);
            if (lt == -1) {
                sb.append(input, i, input.length());
                break;
            }

            sb.append(input, i, lt);

            int gt = input.indexOf('>', lt + 1);
            if (gt == -1) {
                sb.append(input, lt, input.length());
                break;
            }

            String token = input.substring(lt + 1, gt); // e.g. prefixes.success
            String repl = loc.get(token);
            if (repl == null) repl = def.get(token);

            if (repl != null) {
                sb.append(repl);
            } else {
                sb.append('<').append(token).append('>');
            }

            i = gt + 1;
        }

        return sb.toString();
    }

    private static Map<String, String> loadLocale(String localeTag) {
        localeTag = normalizeLocale(localeTag);

        Path file = LANG_DIR.resolve(localeTag + ".yml");
        if (!Files.exists(file) && !DEFAULT_LOCALE.equals(localeTag)) {
            bundles.put(localeTag, Map.of());
            globals.put(localeTag, Map.of());
            return Map.of();
        }

        Map<String, String> msgs = new HashMap<>();
        Map<String, String> g = new HashMap<>();

        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                Object data = new Yaml().load(r);
                if (data instanceof Map<?, ?> map) {
                    // Globals: flatten nested maps into dotted keys (supports nesting)
                    Object gp = map.get(GLOBAL_PLACEHOLDERS_ROOT);
                    if (gp instanceof Map<?, ?> gpMap) {
                        flattenYaml("", gpMap, g);
                    }

                    // Messages: flatten everything else except global_placeholders
                    for (var entry : map.entrySet()) {
                        String k = String.valueOf(entry.getKey());
                        if (GLOBAL_PLACEHOLDERS_ROOT.equals(k)) continue;

                        Object v = entry.getValue();
                        if (v instanceof Map<?, ?> child) {
                            flattenYaml(k, child, msgs);
                        } else if (v instanceof List<?> list) {
                            msgs.put(k, String.join("\n", list.stream().map(String::valueOf).toList()));
                        } else if (v != null) {
                            msgs.put(k, String.valueOf(v));
                        }
                    }
                }
            } catch (Exception e) {
                NPCPuppetmaster.LOGGER.error("[Lang] Failed to load locale file: {} (locale={})", file, localeTag, e);
                // Keep old bundles if we already had them
                return bundles.getOrDefault(localeTag, Map.of());
            }
        }

        bundles.put(localeTag, Map.copyOf(msgs));
        globals.put(localeTag, Map.copyOf(g));
        return bundles.get(localeTag);
    }

    private static void flattenYaml(String prefix, Map<?, ?> in, Map<String, String> out) {
        for (var entry : in.entrySet()) {
            String k = String.valueOf(entry.getKey());
            String fullKey = prefix.isEmpty() ? k : (prefix + "." + k);
            Object v = entry.getValue();

            if (v instanceof Map<?, ?> child) {
                flattenYaml(fullKey, child, out);
            } else if (v instanceof List<?> list) {
                out.put(fullKey, String.join("\n", list.stream().map(String::valueOf).toList()));
            } else if (v != null) {
                out.put(fullKey, String.valueOf(v));
            }
        }
    }

    private static void ensureDefaults() {
        try {
            Files.createDirectories(LANG_DIR);

            for (String locale : BUNDLED_LOCALES) {
                Path target = LANG_DIR.resolve(locale + ".yml");
                if (Files.exists(target)) continue;

                String resourcePath = "assets/" + NPCPuppetmaster.MOD_ID + "/lang/" + locale + ".yml";
                try (var in = Lang.class.getClassLoader().getResourceAsStream(resourcePath)) {
                    if (in == null) {
                        // Don't fail server startup just because an optional locale is missing
                        if (DEFAULT_LOCALE.equals(locale)) {
                            throw new IllegalStateException("Missing bundled lang file: " + resourcePath);
                        }
                        continue;
                    }
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to init lang directory/files", e);
        }
    }

    private static void checkCompleteness(Set<String> loadedTags) {
        Map<String, String> base = bundles.get(DEFAULT_LOCALE);
        if (base == null || base.isEmpty()) {
            NPCPuppetmaster.LOGGER.warn("[Lang] Default locale '{}' is empty or missing; cannot check completeness.",
                    DEFAULT_LOCALE);
            return;
        }

        Set<String> baseKeys = base.keySet();

        for (String tag : loadedTags) {
            if (DEFAULT_LOCALE.equals(tag)) continue;

            Map<String, String> locale = bundles.get(tag);
            if (locale == null || locale.isEmpty()) continue;

            // Missing keys
            List<String> missing = baseKeys.stream()
                    .filter(k -> !locale.containsKey(k))
                    .toList();

            if (!missing.isEmpty()) {
                NPCPuppetmaster.LOGGER.warn(
                        "[Lang] Locale '{}' is missing {} key(s). Example(s): {}",
                        tag,
                        missing.size(),
                        String.join(", ", missing.subList(0, Math.min(missing.size(), MAX_MISSING_KEYS_LOGGED)))
                );
            }

            // Optional: extra keys (often useful during cleanup)
            List<String> extra = locale.keySet().stream()
                    .filter(k -> !baseKeys.contains(k))
                    .toList();

            if (!extra.isEmpty()) {
                NPCPuppetmaster.LOGGER.debug(
                        "[Lang] Locale '{}' has {} extra key(s) not in {} (first few): {}",
                        tag,
                        extra.size(),
                        DEFAULT_LOCALE,
                        String.join(", ", extra.subList(0, Math.min(extra.size(), MAX_MISSING_KEYS_LOGGED)))
                );
            }
        }
    }

    private static String stripAngleBrackets(String s) {
        if (s == null) return "";
        return s.replace("<", "⟨").replace(">", "⟩");
    }

    private Lang() {}
}

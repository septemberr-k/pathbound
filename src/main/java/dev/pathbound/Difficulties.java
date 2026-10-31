package dev.pathbound;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * The difficulties of the running server: config/pathbound/difficulties.json, then KubeJS startup scripts
 * (PathboundEvents.register). Rebuilt when a server starts; clients get a copy on join.
 */
public final class Difficulties {
    private static final Logger LOG = LogUtils.getLogger();
    private static List<Difficulty> server = List.of();
    private static List<Difficulty> client = List.of();

    private Difficulties() {
    }

    /** config/pathbound: difficulties.json and the datapacks/ folder for "config:" packs. */
    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get().resolve("pathbound");
    }

    public static Path configFile() {
        return configDir().resolve("difficulties.json");
    }

    /** Reads the config (writing the default one first if there is none) and applies scripts. */
    static void load() {
        Path file = configFile();
        String json;
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, defaultJson());
                LOG.info("Wrote the default difficulties to {}", file);
            }
            json = Files.readString(file);
        } catch (IOException e) {
            LOG.error("Cannot read {}; using the built-in difficulties", file, e);
            json = defaultJson();
        }
        Map<String, Difficulty.Builder> map;
        try {
            map = parse(json);
        } catch (RuntimeException e) {
            LOG.error("Bad {}: {}; using the built-in difficulties", file, e.toString());
            map = parse(defaultJson());
        }
        try {
            Hooks.register.accept(map);
        } catch (RuntimeException e) {
            LOG.error("A KubeJS difficulty script failed", e);
        }
        set(build(map));
    }

    static Map<String, Difficulty.Builder> parse(String json) {
        Map<String, Difficulty.Builder> map = new LinkedHashMap<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        for (JsonElement e : root.getAsJsonArray("difficulties")) {
            Difficulty.Builder b = Difficulty.fromJson(e.getAsJsonObject());
            if (!Difficulty.validId(b.id)) throw new IllegalArgumentException("bad difficulty id '" + b.id + "' (a-z, 0-9, _; not 'none')");
            if (map.put(b.id, b) != null) throw new IllegalArgumentException("difficulty '" + b.id + "' is defined twice");
        }
        return map;
    }

    static List<Difficulty> build(Map<String, Difficulty.Builder> map) {
        List<Difficulty> list = new ArrayList<>();
        for (Difficulty.Builder b : map.values()) {
            if (!Difficulty.validId(b.id)) {
                LOG.error("Skipping difficulty with bad id '{}'", b.id);
                continue;
            }
            list.add(b.build());
        }
        list.sort(Comparator.comparingInt(Difficulty::order));
        return List.copyOf(list);
    }

    /** Replaces the server list (also used by GameTests). */
    static void set(List<Difficulty> list) {
        server = list;
        LOG.info("Difficulties: {}", list.stream().map(Difficulty::id).toList());
    }

    public static List<Difficulty> all() {
        return server;
    }

    public static Difficulty get(String id) {
        String key = canonical(id);
        for (Difficulty d : server) if (d.id().equals(key)) return d;
        return null;
    }

    /** Lower case; "none" and unknown ids pass through. */
    public static String canonical(String id) {
        return id == null ? Difficulty.NONE : id.toLowerCase(java.util.Locale.ROOT);
    }

    static String defaultJson() {
        try (InputStream in = Difficulties.class.getResourceAsStream("/pathbound/default_difficulties.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("default_difficulties.json missing from the mod jar", e);
        }
    }

    // ---- client copy ----

    static void setClient(List<Difficulty> list) {
        client = list;
    }

    static List<Difficulty> client() {
        return client;
    }

    static Difficulty clientGet(String id) {
        for (Difficulty d : client) if (d.id().equals(id)) return d;
        return null;
    }
}

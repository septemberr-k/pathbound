package dev.pathbound;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * Display text from the config or a script. Three forms:
 * <ul>
 *   <li>a plain string: a literal ("Nightmare");</li>
 *   <li>a text component: {"translate": "my.key"} or {"text": "...", "color": "red"};</li>
 *   <li>one string per language: {"en_us": "Nightmare", "ru_ru": "Кошмар"} (falls back to en_us, then any).</li>
 * </ul>
 * Kept as JSON so the server can send it to clients unchanged; the client resolves it in its own language.
 */
public record Text(JsonElement json) {
    public static final Text EMPTY = literal("");

    public static Text literal(String s) {
        return new Text(new com.google.gson.JsonPrimitive(s));
    }

    public static Text translatable(String key) {
        JsonObject o = new JsonObject();
        o.addProperty("translate", key);
        return new Text(o);
    }

    public static Text of(Map<?, ?> perLanguage) {
        JsonObject o = new JsonObject();
        perLanguage.forEach((k, v) -> o.addProperty(String.valueOf(k), String.valueOf(v)));
        return new Text(o);
    }

    public static Text parse(String json) {
        return new Text(JsonParser.parseString(json));
    }

    /** A JSON object whose keys all look like language codes (en_us, ru_ru, pt_br...) is a per-language map. */
    private boolean perLanguage() {
        if (!json.isJsonObject() || json.getAsJsonObject().isEmpty()) return false;
        for (String k : json.getAsJsonObject().keySet()) if (!k.matches("[a-z]{2,3}_[a-z]{2,3}")) return false;
        return true;
    }

    public Component resolve(String language) {
        if (json.isJsonPrimitive()) return Component.literal(json.getAsString());
        if (perLanguage()) {
            JsonObject o = json.getAsJsonObject();
            JsonElement v = o.has(language) ? o.get(language) : o.has("en_us") ? o.get("en_us") : o.entrySet().iterator().next().getValue();
            return Component.literal(v.getAsString());
        }
        return ComponentSerialization.CODEC.parse(RegistryAccess.EMPTY.createSerializationContext(JsonOps.INSTANCE), json)
                .result().orElseGet(() -> Component.literal(json.toString()));
    }

    /** Server side (chat lines, command replies): components travel as they are, maps resolve to English. */
    public Component server() {
        return resolve("en_us");
    }

    @Override
    public String toString() {
        return json.toString();
    }
}

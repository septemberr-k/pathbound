package dev.pathbound;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One world difficulty, from config/pathbound/difficulties.json or a KubeJS startup script.
 *
 * @param id         lower-case id stored in the world ([a-z0-9_]); "none" is reserved for "not chosen yet"
 * @param stages     player tags (= KubeJS stages) the difficulty grants; other difficulties' stages are removed
 * @param datapacks  packs enabled while this difficulty is active ("builtin:name" = datapacks/name in the mod jar,
 *                   anything else = a pack id as /datapack list shows it); other difficulties' packs are disabled
 * @param commands   server commands run once when the difficulty is chosen
 * @param frame      optional GUI sprite id for this difficulty's card (states: frame, frame_hovered, frame_selected)
 * @param banner     optional drawn title: a texture id, or one per language like a name; drawn at its own size in place
 *                   of the name (the name is still used in chat and the confirmation)
 */
public record Difficulty(String id, Text name, Text description, int color, String icon, int order, boolean selectable,
                         List<String> stages, List<String> datapacks, List<String> commands, String frame, Text banner) {
    public static final String NONE = "none";

    public static boolean validId(String id) {
        return id != null && id.matches("[a-z0-9_]{1,32}") && !id.equals(NONE);
    }

    /** What the client needs to draw the chooser; stages, packs and commands stay on the server. */
    public JsonObject toClientJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.add("name", name.json());
        o.add("description", description.json());
        o.addProperty("color", color);
        if (icon != null) o.addProperty("icon", icon);
        o.addProperty("order", order);
        o.addProperty("selectable", selectable);
        if (frame != null) o.addProperty("frame", frame);
        if (banner != null) o.add("banner", banner.json());
        return o;
    }

    public Builder toBuilder() {
        Builder b = new Builder(id);
        b.name = name;
        b.description = description;
        b.color = color;
        b.icon = icon;
        b.order = order;
        b.selectable = selectable;
        b.stages.addAll(stages);
        b.datapacks.addAll(datapacks);
        b.commands.addAll(commands);
        b.frame = frame;
        b.banner = banner;
        return b;
    }

    /** Mutable form used by the config reader and by KubeJS: every setter returns the builder. */
    public static final class Builder {
        public final String id;
        Text name, description = Text.EMPTY;
        int color = 0xFFFFFFFF, order;
        String icon, frame;
        Text banner;
        boolean selectable = true;
        final List<String> stages = new ArrayList<>(), datapacks = new ArrayList<>(), commands = new ArrayList<>();

        public Builder(String id) {
            this.id = id;
            this.name = Text.literal(id);
            this.order = 100;
        }

        /** A string (literal), a {"translate": ...} component, or a map of language code to text. */
        public Builder name(Object text) {
            name = text(text);
            return this;
        }

        public Builder description(Object text) {
            description = text(text);
            return this;
        }

        public Builder nameKey(String translationKey) {
            name = Text.translatable(translationKey);
            return this;
        }

        public Builder descriptionKey(String translationKey) {
            description = Text.translatable(translationKey);
            return this;
        }

        /** 0xRRGGBB or 0xAARRGGBB as a number, or "#RRGGBB" / "#AARRGGBB". */
        public Builder color(Object c) {
            color = parseColor(c);
            return this;
        }

        /** An item id (minecraft:torch) or a texture (ns:textures/gui/...png). */
        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder selectable(boolean selectable) {
            this.selectable = selectable;
            return this;
        }

        public Builder stage(String stage) {
            stages.add(stage);
            return this;
        }

        public Builder clearStages() {
            stages.clear();
            return this;
        }

        public Builder datapack(String pack) {
            datapacks.add(pack);
            return this;
        }

        public Builder clearDatapacks() {
            datapacks.clear();
            return this;
        }

        public Builder command(String command) {
            commands.add(command);
            return this;
        }

        public Builder frame(String sprite) {
            frame = sprite;
            return this;
        }

        /** A texture id (ns:textures/gui/x.png) or a map of language code to texture id. */
        public Builder banner(Object texture) {
            banner = text(texture);
            return this;
        }

        public Difficulty build() {
            return new Difficulty(id, name, description, color, icon, order, selectable, List.copyOf(stages), List.copyOf(datapacks), List.copyOf(commands), frame, banner);
        }
    }

    static Text text(Object o) {
        if (o instanceof Text t) return t;
        if (o instanceof JsonElement e) return e.isJsonPrimitive() ? Text.literal(e.getAsString()) : new Text(e);
        if (o instanceof Map<?, ?> m) return m.containsKey("translate") ? Text.translatable(String.valueOf(m.get("translate"))) : Text.of(m);
        return Text.literal(String.valueOf(o));
    }

    static int parseColor(Object c) {
        if (c instanceof Number n) {
            long v = n.longValue();
            return (int) (v > 0xFFFFFFL ? v : v | 0xFF000000L);
        }
        String s = String.valueOf(c).trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.startsWith("0x")) s = s.substring(2);
        long v = Long.parseLong(s, 16);
        return (int) (s.length() <= 6 ? v | 0xFF000000L : v);
    }

    /** Reads one entry of difficulties.json. */
    static Builder fromJson(JsonObject o) {
        String id = o.get("id").getAsString();
        Builder b = new Builder(id);
        if (o.has("name")) b.name(o.get("name"));
        if (o.has("description")) b.description(o.get("description"));
        if (o.has("color")) b.color(o.get("color").getAsJsonPrimitive().isNumber() ? o.get("color").getAsLong() : o.get("color").getAsString());
        if (o.has("icon")) b.icon(o.get("icon").getAsString());
        if (o.has("order")) b.order(o.get("order").getAsInt());
        if (o.has("selectable")) b.selectable(o.get("selectable").getAsBoolean());
        if (o.has("frame")) b.frame(o.get("frame").getAsString());
        if (o.has("banner")) b.banner(o.get("banner"));
        strings(o, "stages").forEach(b::stage);
        strings(o, "datapacks").forEach(b::datapack);
        strings(o, "commands").forEach(b::command);
        return b;
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o.has(key)) for (JsonElement e : o.getAsJsonArray(key)) out.add(e.getAsString());
        return out;
    }

    /** Client copy, from {@link #toClientJson()}. */
    static Difficulty fromClientJson(JsonObject o) {
        Builder b = new Builder(o.get("id").getAsString());
        b.name = new Text(o.get("name"));
        b.description = new Text(o.get("description"));
        b.color = o.get("color").getAsInt();
        if (o.has("icon")) b.icon = o.get("icon").getAsString();
        b.order = o.get("order").getAsInt();
        b.selectable = o.get("selectable").getAsBoolean();
        if (o.has("frame")) b.frame = o.get("frame").getAsString();
        if (o.has("banner")) b.banner = new Text(o.get("banner"));
        return b.build();
    }

    static JsonArray toClientJson(List<Difficulty> list) {
        JsonArray a = new JsonArray();
        list.forEach(d -> a.add(d.toClientJson()));
        return a;
    }
}

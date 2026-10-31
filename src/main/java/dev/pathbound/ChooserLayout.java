package dev.pathbound;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.Reader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;

/**
 * Look of the chooser, from assets/pathbound/chooser/layout.json in any resource pack or in kubejs/assets.
 * Every key is optional; without the file the chooser uses these defaults (plain cards, no sprites). Colors are
 * "#RRGGBB" / "#AARRGGBB" strings or numbers; texts are like difficulty names (string, component or per-language map).
 */
public record ChooserLayout(
        boolean blur, ResourceLocation bgTexture, boolean bgTile, int bgColor,
        Text title, int titleColor, Text subtitle, int subtitleColor,
        int cardWidth, int cardMinHeight, int spacing, int columns, int padding,
        ResourceLocation frame, boolean tintFrame, int cardColor, int cardHoverColor,
        int textColor, Integer nameColor, Text hint, int hintColor, boolean icons, boolean centerText, int frameInset,
        ResourceLocation hoverSound, ResourceLocation clickSound, boolean confirm, Animations anim) {

    /**
     * "animations": false turns everything off; an object tunes it. Times in milliseconds, distances in GUI pixels.
     * easing: linear | ease_out | ease_in_out | ease_out_back.
     */
    public record Animations(boolean enabled, int fadeMs, int introMs, int staggerMs, int introOffset, Anim.Easing introEasing,
                             int hoverLift, int hoverMs, int pressMs, double pressScale, boolean glow,
                             int hintPulseMs, int selectedPulseMs, int iconBob) {
        static final Animations OFF = new Animations(false, 0, 0, 0, 0, Anim.Easing.LINEAR, 0, 0, 0, 1, false, 0, 0, 0);

        static Animations parse(JsonObject root) {
            if (root.has("animations") && root.get("animations").isJsonPrimitive() && !root.get("animations").getAsBoolean()) return OFF;
            JsonObject a = obj(root, "animations");
            if (!bool(a, "enabled", true)) return OFF;
            return new Animations(true, integer(a, "fade_ms", 250), integer(a, "intro_ms", 380), integer(a, "stagger_ms", 70),
                    integer(a, "intro_offset", 14), Anim.Easing.byName(a.has("easing") ? a.get("easing").getAsString() : "ease_out_back"),
                    integer(a, "hover_lift", 4), integer(a, "hover_ms", 70), integer(a, "press_ms", 140),
                    a.has("press_scale") ? a.get("press_scale").getAsDouble() : 0.96, bool(a, "glow", true),
                    integer(a, "hint_pulse_ms", 1400), integer(a, "selected_pulse_ms", 900), integer(a, "icon_bob", 1));
        }
    }

    private static final Logger LOG = LogUtils.getLogger();
    static final ResourceLocation FILE = Pathbound.id("chooser/layout.json");
    static final ChooserLayout DEFAULT = parse(new JsonObject());
    private static ChooserLayout current = DEFAULT;

    static ChooserLayout get() {
        return current;
    }

    /** Reloaded with the other client resources (F3+T, resource pack changes). */
    static final ResourceManagerReloadListener RELOADER = new ResourceManagerReloadListener() {
        @Override
        public void onResourceManagerReload(ResourceManager manager) {
            ChooserScreen.clearSizes();
            current = manager.getResource(FILE).map(r -> {
                try (Reader in = r.openAsReader()) {
                    ChooserLayout l = parse(JsonParser.parseReader(in).getAsJsonObject());
                    LOG.info("Chooser layout from {} ({})", FILE, r.sourcePackId());
                    return l;
                } catch (Exception e) {
                    LOG.error("Bad {} in {}: {}; using the default layout", FILE, r.sourcePackId(), e.toString());
                    return DEFAULT;
                }
            }).orElse(DEFAULT);
        }
    };

    static ChooserLayout parse(JsonObject root) {
        JsonObject bg = obj(root, "background"), title = obj(root, "title"), sub = obj(root, "subtitle"),
                card = obj(root, "card"), sounds = obj(root, "sounds");
        return new ChooserLayout(
                bool(bg, "blur", true), rl(bg, "texture", null), bool(bg, "tile", false), color(bg, "color", 0),
                text(title, "text", Text.translatable("pathbound.title")), color(title, "color", 0xFFE8C46A),
                text(sub, "text", Text.translatable("pathbound.subtitle")), color(sub, "color", 0xFFA0A0A0),
                integer(card, "width", 190), integer(card, "min_height", 110), integer(card, "spacing", 14),
                integer(card, "columns", 0), integer(card, "padding", 12),
                rl(card, "frame", null), bool(card, "tint_frame", false),
                color(card, "color", 0xC0101018), color(card, "hover_color", 0xE0201A2C),
                color(card, "text_color", 0xFFD8D8D8), card.has("name_color") ? color(card, "name_color", 0) : null,
                text(card, "hint", Text.translatable("pathbound.pick")), color(card, "hint_color", 0xFF7A7A86),
                bool(card, "icons", true), "center".equals(card.has("text_align") ? card.get("text_align").getAsString() : "left"), integer(card, "frame_inset", 0),
                rl(sounds, "hover", null), rl(sounds, "click", ResourceLocation.withDefaultNamespace("ui.button.click")),
                bool(root, "confirm", true), Animations.parse(root));
    }

    private static JsonObject obj(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : new JsonObject();
    }

    private static boolean bool(JsonObject o, String k, boolean def) {
        return o.has(k) ? o.get(k).getAsBoolean() : def;
    }

    private static int integer(JsonObject o, String k, int def) {
        return o.has(k) ? o.get(k).getAsInt() : def;
    }

    private static int color(JsonObject o, String k, int def) {
        if (!o.has(k)) return def;
        JsonElement e = o.get(k);
        return Difficulty.parseColor(e.getAsJsonPrimitive().isNumber() ? (Object) e.getAsLong() : e.getAsString());
    }

    private static Text text(JsonObject o, String k, Text def) {
        return o.has(k) ? Difficulty.text(o.get(k)) : def;
    }

    private static ResourceLocation rl(JsonObject o, String k, ResourceLocation def) {
        if (!o.has(k) || o.get(k).isJsonNull()) return def;
        String s = o.get(k).getAsString();
        return s.isEmpty() ? null : ResourceLocation.parse(s);
    }
}

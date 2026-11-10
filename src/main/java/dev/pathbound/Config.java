package dev.pathbound;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config: config/pathbound-server.toml, read when a world (integrated or dedicated server) starts. */
public final class Config {
    static final ModConfigSpec SPEC;
    static final ModConfigSpec.ConfigValue<String> DEFAULT_MODE;
    static final ModConfigSpec.IntValue CHOOSE_PERMISSION;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        DEFAULT_MODE = b.comment(
                        "Difficulty for a world that has none yet: \"none\" or a difficulty id from",
                        "config/pathbound/difficulties.json (out of the box: \"classic\" or \"trial\").",
                        "\"none\" asks the host (or an operator) with the chooser on first join.",
                        "An id is applied when the server starts and the chooser is skipped.",
                        "A world that already has a difficulty keeps it.")
                .define("defaultMode", "none", o -> o instanceof String s && s.matches("[A-Za-z0-9_]{1,32}"));
        CHOOSE_PERMISSION = b.comment(
                        "Permission level a player needs to choose the difficulty on a server: 2 = operators (default),",
                        "0 = any player (the first one to choose decides for the world). The owner of a singleplayer or",
                        "LAN world can always choose. /pathbound mode set and /pathbound choose still need level 2.")
                .defineInRange("choosePermission", 2, 0, 4);
        SPEC = b.build();
    }

    private Config() {
    }

    static String defaultMode() {
        return Difficulties.canonical(DEFAULT_MODE.get());
    }

    static int choosePermission() {
        return CHOOSE_PERMISSION.get();
    }
}

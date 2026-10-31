package dev.pathbound;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config: config/pathbound-server.toml, read when a world (integrated or dedicated server) starts. */
public final class Config {
    static final ModConfigSpec SPEC;
    static final ModConfigSpec.ConfigValue<String> DEFAULT_MODE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        DEFAULT_MODE = b.comment(
                        "Difficulty for a world that has none yet: \"none\" or a difficulty id from",
                        "config/pathbound/difficulties.json (out of the box: \"classic\" or \"trial\").",
                        "\"none\" asks the host (or an operator) with the chooser on first join.",
                        "An id is applied when the server starts and the chooser is skipped.",
                        "A world that already has a difficulty keeps it.")
                .define("defaultMode", "none", o -> o instanceof String s && (s.equals("none") || s.matches("[a-z0-9_]{1,32}")));
        SPEC = b.build();
    }

    private Config() {
    }

    static String defaultMode() {
        return Difficulties.canonical(DEFAULT_MODE.get());
    }
}

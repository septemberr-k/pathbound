package dev.pathbound;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Extension points. The optional KubeJS module (compat.kubejs) fills them with its script events; other mods may
 * chain onto them in their constructor. All but {@link #register} run on the server thread.
 */
public final class Hooks {
    /** Before the list is built: add, change or remove difficulties (KubeJS: PathboundEvents.register). */
    public static Consumer<Map<String, Difficulty.Builder>> register = m -> {
    };
    /** A player asks for a difficulty in the chooser; false refuses (KubeJS: PathboundEvents.beforeChoose). */
    public static BeforeChoose beforeChoose = (player, difficulty) -> true;
    /** The world got a difficulty; player is null when the config or a command set it. */
    public static AfterChoose afterChoose = (server, player, difficulty) -> {
    };
    /** A player joined a world that has a difficulty. */
    public static PlayerJoin playerJoin = (player, difficulty) -> {
    };

    private Hooks() {
    }

    @FunctionalInterface
    public interface BeforeChoose {
        boolean allow(ServerPlayer player, Difficulty difficulty);
    }

    @FunctionalInterface
    public interface AfterChoose {
        void chosen(MinecraftServer server, ServerPlayer player, Difficulty difficulty);
    }

    @FunctionalInterface
    public interface PlayerJoin {
        void joined(ServerPlayer player, Difficulty difficulty);
    }
}

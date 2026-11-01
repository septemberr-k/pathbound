package dev.pathbound.compat.kubejs;

import dev.pathbound.Difficulties;
import dev.pathbound.Difficulty;
import dev.pathbound.DifficultyData;
import dev.pathbound.DifficultyApi;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/** The "Pathbound" binding for scripts. */
public final class PathboundBinding {
    private PathboundBinding() {
    }

    /** The world's difficulty id, "none" if not chosen yet. */
    public static String get(MinecraftServer server) {
        return DifficultyData.get(server).mode();
    }

    /**
     * The running world's difficulty id without a server at hand ("none" if not chosen, or while the first data load
     * of a world runs, before the world exists). For recipes use PathboundEvents.recipes, which handles that load.
     */
    public static String current() {
        String mode = DifficultyApi.currentOrNull();
        return mode == null ? Difficulty.NONE : mode;
    }

    public static boolean isChosen(MinecraftServer server) {
        return DifficultyData.get(server).chosen();
    }

    /** Sets the world's difficulty ("none" resets it); false for an unknown id. Applies stages and datapacks. */
    public static boolean set(MinecraftServer server, String id) {
        return DifficultyApi.set(server, id);
    }

    public static List<String> ids() {
        return Difficulties.all().stream().map(Difficulty::id).toList();
    }

    public static Difficulty info(String id) {
        return Difficulties.get(id);
    }
}

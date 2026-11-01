package dev.pathbound;

import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Java API for other mods and the KubeJS binding. Call on the server thread. */
public final class DifficultyApi {
    private DifficultyApi() {
    }

    /** The world's difficulty id, or "none". */
    public static String get(MinecraftServer server) {
        return DifficultyData.get(server).mode();
    }

    /**
     * The running server's world difficulty id ("none" if not chosen), or null when no world is up yet: the first data
     * load of a world happens before the world exists.
     */
    public static String currentOrNull() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.overworld() == null) return null;
        return DifficultyData.get(server).mode();
    }

    /** The difficulties of the running server, in chooser order. */
    public static List<Difficulty> difficulties() {
        return Difficulties.all();
    }

    /** Sets the difficulty like /pathbound mode set (stages, datapacks, commands, afterChoose); false for an unknown id. */
    public static boolean set(MinecraftServer server, String id) {
        String mode = Difficulties.canonical(id);
        if (!mode.equals(Difficulty.NONE) && Difficulties.get(mode) == null) return false;
        ServerLogic.setMode(server, mode, null);
        return true;
    }
}

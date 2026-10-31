package dev.pathbound.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.pathbound.Difficulty;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** afterChoose: e.server, e.player (null when the config or /pathbound mode set chose it), e.difficulty, e.difficultyInfo. */
public final class ChosenEventJS implements KubeEvent {
    private final MinecraftServer server;
    private final ServerPlayer player;
    private final Difficulty difficulty;

    ChosenEventJS(MinecraftServer server, ServerPlayer player, Difficulty difficulty) {
        this.server = server;
        this.player = player;
        this.difficulty = difficulty;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public String getDifficulty() {
        return difficulty.id();
    }

    public Difficulty getDifficultyInfo() {
        return difficulty;
    }
}

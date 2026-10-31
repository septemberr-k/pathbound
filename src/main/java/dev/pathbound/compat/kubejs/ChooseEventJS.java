package dev.pathbound.compat.kubejs;

import dev.latvian.mods.kubejs.player.KubePlayerEvent;
import dev.pathbound.Difficulty;
import net.minecraft.server.level.ServerPlayer;

/** beforeChoose (cancellable: e.cancel() refuses) and playerJoin: e.player, e.difficulty (id), e.difficultyInfo. */
public final class ChooseEventJS implements KubePlayerEvent {
    private final ServerPlayer player;
    private final Difficulty difficulty;

    ChooseEventJS(ServerPlayer player, Difficulty difficulty) {
        this.player = player;
        this.difficulty = difficulty;
    }

    @Override
    public ServerPlayer getEntity() {
        return player;
    }

    public String getDifficulty() {
        return difficulty.id();
    }

    public Difficulty getDifficultyInfo() {
        return difficulty;
    }
}

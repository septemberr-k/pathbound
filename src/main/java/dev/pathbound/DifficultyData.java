package dev.pathbound;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Stored in saves/<world>/data/pathbound.dat, so the choice travels with the world. */
public final class DifficultyData extends SavedData {
    private static final Factory<DifficultyData> FACTORY = new Factory<>(DifficultyData::new, DifficultyData::load);

    private String mode = Difficulty.NONE;

    public static DifficultyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, Pathbound.MODID);
    }

    static DifficultyData load(CompoundTag tag, HolderLookup.Provider registries) {
        DifficultyData data = new DifficultyData();
        String stored = tag.getString("mode");
        data.mode = stored.isEmpty() ? Difficulty.NONE : Difficulties.canonical(stored);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("mode", mode);
        return tag;
    }

    /** The difficulty id, or "none". It may name a difficulty that was removed from the config since. */
    public String mode() {
        return mode;
    }

    public boolean chosen() {
        return !mode.equals(Difficulty.NONE);
    }

    public void setMode(String mode) {
        this.mode = mode;
        setDirty();
    }
}

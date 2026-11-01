package dev.pathbound;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side behaviour of the difficulty choice. Each test leaves the world undecided with no difficulty pack on. */
@GameTestHolder(Pathbound.MODID)
@PrefixGameTestTemplate(false)
public final class DifficultyGameTests {
    private static final String HARD_PACK = "pathbound/config/test_trial";
    private static final String TEST_PACK = "mod/pathbound:datapacks/test_nightmare";
    /** The mock player of the custom-difficulty test; removed when its batch ends, pass or fail. */
    private static ServerPlayer customPlayer;

    private DifficultyGameTests() {
    }

    @GameTest(template = "empty", batch = "data")
    public static void savedDataRoundTripsThroughDisk(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        try {
            for (String mode : List.of("none", "classic", "trial", "nightmare")) {
                var data = new DifficultyData();
                data.setMode(mode);
                h.assertTrue(data.isDirty(), "setMode marks the data dirty");
                var file = Files.createTempFile("pathbound", ".dat");
                try {
                    // SavedData.save(File) writes on NeoForge's IO worker; write the same tag synchronously here.
                    NbtIo.writeCompressed(data.save(new CompoundTag(), registries), file);
                    var tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
                    h.assertTrue(DifficultyData.load(tag, registries).mode().equals(mode), "Difficulty " + mode + " survives save and load");
                } finally {
                    Files.deleteIfExists(file);
                }
            }
            h.assertTrue(DifficultyData.load(new CompoundTag(), registries).mode().equals("none"), "Missing key reads as none");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "parse")
    public static void configParsesAllTextAndColorForms(GameTestHelper h) {
        var map = Difficulties.parse("""
                {"difficulties": [
                  {"id": "a", "name": "Plain", "description": {"translate": "pathbound.desc.trial"}, "color": "#FF4040"},
                  {"id": "b", "name": {"en_us": "Night", "ru_ru": "Ночь"}, "color": 4227327, "selectable": false,
                   "stages": ["s1", "s2"], "datapacks": ["builtin:x", "file/y.zip"], "commands": ["say hi"], "order": -1}
                ]}""");
        var list = Difficulties.build(map);
        h.assertTrue(list.size() == 2 && list.get(0).id().equals("b"), "Sorted by order");
        var a = list.get(1);
        var b = list.get(0);
        h.assertTrue(a.name().resolve("ru_ru").getString().equals("Plain"), "A string is a literal");
        h.assertTrue(a.description().resolve("en_us").getContents() instanceof TranslatableContents, "{translate} is a translatable component");
        h.assertTrue(a.color() == 0xFFFF4040 && b.color() == (0xFF000000 | 4227327), "Colors: hex string and number get full alpha");
        h.assertTrue(b.name().resolve("ru_ru").getString().equals("Ночь") && b.name().resolve("de_de").getString().equals("Night"), "Per-language map, en_us fallback");
        h.assertTrue(!b.selectable() && b.stages().equals(List.of("s1", "s2")) && b.commands().equals(List.of("say hi")), "Flags, stages, commands");
        h.assertTrue(ServerLogic.packId("builtin:x").equals("mod/pathbound:datapacks/x") && ServerLogic.packId("config:z").equals("pathbound/config/z") && ServerLogic.packId("config:z").equals("pathbound/config/z") && ServerLogic.packId("file/y.zip").equals("file/y.zip"), "Pack ids");
        boolean rejected = false;
        try {
            Difficulties.parse("{\"difficulties\": [{\"id\": \"none\"}]}");
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        h.assertTrue(rejected, "The id none is reserved");
        h.succeed();
    }

    /** Script recipes (Hooks.needsDataReload) get a data reload on a choice even when no datapack changes. */
    @GameTest(template = "empty", batch = "scriptReload", timeoutTicks = 400)
    public static void aDifficultyWithScriptRecipesReloadsData(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        var before = Hooks.needsDataReload;
        // A data reload replaces the server's resources object; a small test world may reload within the same tick.
        Object[] resources = new Object[1];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(!ServerLogic.reloadPending(), "No reload before the test"))
                .thenExecute(() -> {
                    resources[0] = server.getServerResources();
                    Hooks.needsDataReload = mode -> mode.equals("classic");
                    ServerLogic.setMode(server, "classic", null);
                })
                .thenWaitUntil(() -> h.assertTrue(!ServerLogic.reloadPending() && server.getServerResources() != resources[0],
                        "classic has no datapacks, but the hook asked for a reload"))
                .thenExecute(() -> {
                    resources[0] = server.getServerResources();
                    Hooks.needsDataReload = before;
                    ServerLogic.setMode(server, "none", null);
                })
                .thenExecuteAfter(20, () -> h.assertTrue(server.getServerResources() == resources[0], "Without the hook, none (no packs) does not reload"))
                .thenSucceed();
    }

    @AfterBatch(batch = "scriptReload")
    public static void restoreAfterScriptReload(ServerLevel level) {
        Hooks.needsDataReload = mode -> false;
        restoreAfterPack(level);
    }

    @GameTest(template = "empty", batch = "stages", timeoutTicks = 400)
    public static void choosingTrialSwapsThePlayerStage(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        try {
            player.addTag("pathbound_classic");
            ServerLogic.setMode(server, "trial", null);
            h.assertTrue(DifficultyData.get(server).mode().equals("trial"), "Stored difficulty is trial");
            h.assertTrue(player.getTags().contains("pathbound_trial"), "Tag pathbound_trial added");
            h.assertTrue(!player.getTags().contains("pathbound_classic"), "Tag pathbound_classic removed");
            ServerLogic.setMode(server, "classic", null);
            h.assertTrue(player.getTags().contains("pathbound_classic") && !player.getTags().contains("pathbound_trial"), "Back to pathbound_classic only");
        } finally {
            server.getPlayerList().remove(player);
            player.discard();
            ServerLogic.setMode(server, "none", null);
        }
        // Two reloads were requested back to back; the packs must end up matching the final difficulty.
        h.succeedWhen(() -> h.assertTrue(!ServerLogic.reloadPending() && !selected(server, HARD_PACK) && torchCount(server) == 4, "After none the trial pack is off and torches give 4"));
    }

    @GameTest(template = "empty", batch = "pack", timeoutTicks = 600)
    public static void trialPackFollowsTheMode(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        h.assertTrue(server.getPackRepository().isAvailable(HARD_PACK), "Trial pack is registered; available: " + server.getPackRepository().getAvailableIds());
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(!ServerLogic.reloadPending() && !selected(server, HARD_PACK), "Pack off before the test"))
                .thenExecute(() -> ServerLogic.setMode(server, "trial", null))
                .thenWaitUntil(() -> h.assertTrue(!ServerLogic.reloadPending() && selected(server, HARD_PACK), "trial selects the trial pack"))
                .thenExecute(() -> h.assertTrue(torchCount(server) == 2, "trial torch recipe gives 2, got " + torchCount(server)))
                .thenExecute(() -> ServerLogic.setMode(server, "classic", null))
                .thenWaitUntil(() -> h.assertTrue(!ServerLogic.reloadPending() && !selected(server, HARD_PACK), "classic deselects the trial pack"))
                .thenExecute(() -> h.assertTrue(torchCount(server) == 4, "classic torch recipe gives 4, got " + torchCount(server)))
                .thenExecute(() -> ServerLogic.setMode(server, "none", null))
                .thenSucceed();
    }

    /** A third difficulty from data: its own stage, built-in pack and command; switching away undoes pack and stage. */
    @GameTest(template = "empty", batch = "custom", timeoutTicks = 800)
    public static void aThirdDifficultyWorksLikeTheOthers(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        var map = Difficulties.parse(readConfig());
        map.put("nightmare", new Difficulty.Builder("nightmare").name(Map.of("en_us", "Nightmare")).color("#FF4040").order(2)
                .stage("pathbound_nightmare").datapack("builtin:test_nightmare").command("scoreboard objectives add pathbound_test_cmd dummy"));
        Difficulties.set(Difficulties.build(map));
        customPlayer = h.makeMockServerPlayerInLevel();
        ServerPlayer player = customPlayer;
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(!ServerLogic.reloadPending() && !selected(server, HARD_PACK), "No difficulty pack before the test"))
                .thenExecute(() -> {
                    h.assertTrue(server.getPackRepository().isAvailable(TEST_PACK), "Built-in test pack found; available: " + server.getPackRepository().getAvailableIds());
                    ServerLogic.setMode(server, "nightmare", null);
                    h.assertTrue(player.getTags().contains("pathbound_nightmare") && !player.getTags().contains("pathbound_trial"), "nightmare stage only");
                })
                .thenWaitUntil(() -> h.assertTrue(!ServerLogic.reloadPending() && selected(server, TEST_PACK) && !selected(server, HARD_PACK), "nightmare selects its own pack only"))
                .thenExecute(() -> h.assertTrue(torchCount(server) == 1, "nightmare torch recipe gives 1, got " + torchCount(server)))
                .thenWaitUntil(() -> h.assertTrue(server.getScoreboard().getObjective("pathbound_test_cmd") != null, "The difficulty's command ran after the reload"))
                .thenExecute(() -> {
                    ServerLogic.setMode(server, "trial", null);
                    h.assertTrue(player.getTags().contains("pathbound_trial") && !player.getTags().contains("pathbound_nightmare"), "trial replaces the nightmare stage");
                })
                .thenWaitUntil(() -> h.assertTrue(!ServerLogic.reloadPending() && selected(server, HARD_PACK) && !selected(server, TEST_PACK), "trial swaps the packs"))
                .thenExecute(() -> h.assertTrue(torchCount(server) == 2, "trial torch recipe gives 2, got " + torchCount(server)))
                .thenExecute(() -> restoreAfterCustom(h.getLevel()))
                .thenWaitUntil(() -> h.assertTrue(!ServerLogic.reloadPending() && !selected(server, HARD_PACK) && !selected(server, TEST_PACK), "Packs off after the test"))
                .thenSucceed();
    }

    @AfterBatch(batch = "custom")
    public static void restoreAfterCustom(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (customPlayer != null) {
            server.getPlayerList().remove(customPlayer);
            customPlayer.discard();
            customPlayer = null;
        }
        var objective = server.getScoreboard().getObjective("pathbound_test_cmd");
        if (objective != null) server.getScoreboard().removeObjective(objective);
        if (DifficultyData.get(server).chosen()) ServerLogic.setMode(server, "none", null);
        Difficulties.load();
    }

    @GameTest(template = "empty", batch = "config", timeoutTicks = 600)
    public static void configuredDefaultAppliesOnlyToNewWorlds(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(!ServerLogic.reloadPending() && !selected(server, HARD_PACK), "Pack off before the test"))
                .thenExecute(() -> {
                    h.assertTrue(!DifficultyData.get(server).chosen() && Config.defaultMode().equals("none"), "Starts undecided with defaultMode none");
                    ServerLogic.start(server);
                    h.assertTrue(!DifficultyData.get(server).chosen(), "defaultMode none leaves the world undecided");
                    Config.DEFAULT_MODE.set("nonexistent");
                    ServerLogic.start(server);
                    h.assertTrue(!DifficultyData.get(server).chosen(), "An unknown defaultMode is ignored");
                    Config.DEFAULT_MODE.set("Trial");
                    ServerLogic.start(server);
                    h.assertTrue(DifficultyData.get(server).mode().equals("trial"), "defaultMode (any case) is applied to a world without a difficulty");
                })
                .thenWaitUntil(() -> h.assertTrue(!ServerLogic.reloadPending() && selected(server, HARD_PACK), "defaultMode trial enables the trial pack"))
                .thenExecute(() -> {
                    Config.DEFAULT_MODE.set("classic");
                    ServerLogic.start(server);
                    h.assertTrue(DifficultyData.get(server).mode().equals("trial"), "A world that already has a difficulty keeps it");
                    h.assertTrue(!ServerLogic.reloadPending() && selected(server, HARD_PACK), "and keeps its pack");
                })
                .thenExecute(() -> restoreAfterConfig(h.getLevel()))
                .thenWaitUntil(() -> h.assertTrue(!ServerLogic.reloadPending() && !selected(server, HARD_PACK), "Pack off after the test"))
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = "commands", timeoutTicks = 600)
    public static void commandsSetAndClearTheDifficulty(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        var console = server.createCommandSourceStack();
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        try {
            h.assertTrue(ServerLogic.mayChange(console), "The console may change the difficulty");
            h.assertTrue(!ServerLogic.mayChange(player.createCommandSourceStack()), "A player without permission may not");
        } finally {
            server.getPlayerList().remove(player);
            player.discard();
        }
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(!ServerLogic.reloadPending() && !selected(server, HARD_PACK), "Pack off before the test"))
                .thenExecute(() -> {
                    server.getCommands().performPrefixedCommand(console, "pathbound mode set Trial");
                    h.assertTrue(DifficultyData.get(server).mode().equals("trial"), "/pathbound mode set Trial sets trial");
                })
                .thenWaitUntil(() -> h.assertTrue(!ServerLogic.reloadPending() && selected(server, HARD_PACK), "and enables the trial pack"))
                .thenExecute(() -> {
                    server.getCommands().performPrefixedCommand(console, "pathbound choose");
                    h.assertTrue(!DifficultyData.get(server).chosen(), "/pathbound choose clears the difficulty");
                })
                .thenWaitUntil(() -> h.assertTrue(!ServerLogic.reloadPending() && !selected(server, HARD_PACK), "and disables the pack"))
                .thenSucceed();
    }

    @AfterBatch(batch = "commands")
    public static void restoreAfterCommands(ServerLevel level) {
        restoreAfterPack(level);
    }

    @AfterBatch(batch = "config")
    public static void restoreAfterConfig(ServerLevel level) {
        Config.DEFAULT_MODE.set("none");
        restoreAfterPack(level);
    }

    @AfterBatch(batch = "pack")
    public static void restoreAfterPack(ServerLevel level) {
        if (DifficultyData.get(level.getServer()).chosen()) ServerLogic.setMode(level.getServer(), "none", null);
    }

    @AfterBatch(batch = "stages")
    public static void restoreAfterStages(ServerLevel level) {
        restoreAfterPack(level);
    }

    /** The run's config/pathbound/difficulties.json (trial has the test pack), not the jar template. */
    static String readConfig() {
        try {
            return Files.readString(Difficulties.configFile());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean selected(MinecraftServer server, String pack) {
        return server.getPackRepository().getSelectedIds().contains(pack);
    }

    static int torchCount(MinecraftServer server) {
        return server.getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("torch"))
                .map(r -> r.value().getResultItem(server.registryAccess()).getCount()).orElse(-1);
    }
}

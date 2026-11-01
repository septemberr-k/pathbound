package dev.pathbound;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * Opt-in scripted client run (-Dpathbound.difficultyValidation=&lt;difficulty id&gt;): a fresh flat world, the real chooser and
 * confirmation screens driven through their own widgets, then the server and client state after the choice.
 * -Dpathbound.expectCards=N checks the number of cards, -Dpathbound.refuseFirst=&lt;id&gt; first picks a difficulty a script must
 * refuse, -Dpathbound.expectTorch=N the torch recipe count, -Dpathbound.expectTag=tag a tag a script adds after the choice,
 * -Dpathbound.sizes=true adds chooser frames at other window sizes, -Dpathbound.animFrames=true frames of the animations,
 * -Dpathbound.testChoose=true runs /pathbound choose as the world owner without cheats. -Dpathbound.expectRecipes=ids /
 * -Dpathbound.expectNoRecipes=ids check script recipes (PathboundEvents.recipes), -Dpathbound.rejoin=true leaves and
 * reopens the world after the choice and checks them again.
 * Frames and proof.txt go to -Dpathbound.validationOutput. Dev-only source set; never in the jar.
 */
@EventBusSubscriber(modid = Pathbound.MODID, value = Dist.CLIENT)
public final class DifficultyValidation {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String CHOICE = System.getProperty("pathbound.difficultyValidation");
    /** The world created for this run, reopened by -Dpathbound.rejoin=true. */
    private static String levelId;
    private static final String REFUSE = System.getProperty("pathbound.refuseFirst", "");
    private static final List<String> PROOF = new ArrayList<>();
    private static final List<String> CHAT = new ArrayList<>();
    /** width, height, GUI scale, Russian (1) or English (0) */
    private static final int[][] SIZES = {{854, 480, 2, 0}, {1920, 1080, 2, 0}, {1920, 1080, 3, 0}, {854, 480, 3, 1}, {1280, 720, 2, 1}};
    private static int ticks, step, at, questStart, size, sizeTick;
    private static boolean done, refused;
    private static String clicking;
    private static CompletableFuture<Void> reloading;
    private static volatile String serverReport;
    private static ChooserScreen animScreen;

    private DifficultyValidation() {
    }

    @SubscribeEvent
    public static void chat(ClientChatReceivedEvent.System event) {
        if (CHOICE != null) CHAT.add(event.getMessage().getString());
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (CHOICE == null || done) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (++ticks > 6000) fail("Timed out at step " + step + ", screen " + mc.screen);
        if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) {
            onboarding.onClose();
            return;
        }
        switch (step) {
            case 0 -> {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                var rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                var settings = new LevelSettings("Difficulty " + CHOICE, GameType.SURVIVAL, false, net.minecraft.world.Difficulty.PEACEFUL, false, rules, WorldDataConfiguration.DEFAULT);
                levelId = "pathbound-" + CHOICE + "-" + System.currentTimeMillis();
                mc.createWorldOpenFlows().createFreshLevel(levelId, settings, new WorldOptions(1937L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
                next("Created a fresh FLAT world");
            }
            case 1 -> {
                // The chooser must open by itself once the server greets the player.
                if (!(mc.screen instanceof ChooserScreen chooser) || mc.player == null || mc.getOverlay() != null) return;
                if (ticks - at < 60 || mc.levelRenderer.countRenderedSections() < 4) return;
                require(ClientLogic.mode.equals(Difficulty.NONE), "Server reported " + ClientLogic.mode + " in a new world");
                int expectCards = Integer.getInteger("pathbound.expectCards", 2);
                require(chooser.cardCount() == expectCards, "Chooser shows " + chooser.cardCount() + " cards, expected " + expectCards);
                require(Difficulties.clientGet(CHOICE) != null, "The server sent no difficulty " + CHOICE + "; got " + Difficulties.client().stream().map(Difficulty::id).toList());
                for (Difficulty d : Difficulties.client()) {
                    ResourceLocation frame = chooser.frameOf(d.id());
                    PROOF.add("card " + d.id() + ": " + (frame == null ? "plain fallback frame" : "sprite " + frame));
                }
                askServer();
                frame("01-chooser");
                // Escape must not dismiss the chooser.
                mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                require(mc.screen instanceof ChooserScreen, "Escape closed the chooser: " + mc.screen);
                next("Chooser with " + chooser.cardCount() + " cards opened on join; Escape kept it open");
            }
            case 2 -> {
                if (serverReport == null || !(mc.screen instanceof ChooserScreen chooser)) return;
                if (ticks - at < 20) return;
                require(serverReport.startsWith("mode=none "), "Server before the choice: " + serverReport);
                PROOF.add("server before choice: " + serverReport);
                serverReport = null;
                clicking = !REFUSE.isEmpty() && !refused ? REFUSE : CHOICE;
                int[] c = chooser.cardCenter(clicking);
                require(c != null, "No card for " + clicking);
                require(chooser.mouseClicked(c[0], c[1], 0), "Click on the " + clicking + " card was not handled");
                next("Clicked the " + clicking + " card at " + c[0] + "," + c[1]);
            }
            case 3 -> {
                // the card dips first (press animation), then the confirmation opens
                if (!(mc.screen instanceof ConfirmScreen)) {
                    if (ticks - at > 100) fail("No ConfirmScreen after clicking " + clicking + "; screen " + mc.screen);
                    return;
                }
                if (ticks - at < 20) return;
                frame(clicking.equals(CHOICE) ? "02-confirm" : "02-confirm-" + clicking);
                Button yes = mc.screen.children().stream().filter(c -> c instanceof Button b && b.getMessage().equals(CommonComponents.GUI_YES))
                        .map(Button.class::cast).findFirst().orElseThrow(() -> new IllegalStateException("No Yes button on " + mc.screen));
                yes.onPress();
                next("Pressed Yes for " + clicking);
                if (!clicking.equals(CHOICE)) step = 20;
            }
            case 20 -> {
                // A server script must refuse this one: the chooser comes back and the world stays undecided.
                if (!(mc.screen instanceof ChooserScreen)) {
                    if (ticks - at > 400) fail("The chooser did not come back after choosing " + clicking + "; difficulty " + ClientLogic.mode);
                    return;
                }
                require(ClientLogic.mode.equals(Difficulty.NONE), "Server accepted " + ClientLogic.mode + " although a script should refuse it");
                if (ticks - at < 30) return;
                require(CHAT.stream().anyMatch(l -> l.startsWith("You cannot choose")), "No refusal message in chat: " + CHAT);
                refused = true;
                frame("02b-refused");
                askServer();
                PROOF.add("refused " + clicking + " by beforeChoose; chat: " + CHAT);
                next("The beforeChoose script refused " + clicking + "; the chooser is back");
                step = 2;
            }
            case 4 -> {
                // Wait for the server's reply rather than a fixed delay.
                if (!ClientLogic.mode.equals(CHOICE)) return;
                require(mc.screen == null, "Screen still open after the server reported " + CHOICE + ": " + mc.screen);
                next("Server reported " + CHOICE + " to the client");
            }
            case 5 -> {
                // The recipe update follows the data reload; wait for it.
                int expected = Integer.getInteger("pathbound.expectTorch", CHOICE.equals("hard") ? 2 : 4);
                int count = clientTorchCount(mc);
                if (count != expected) {
                    if (ticks - at > 1200) fail("Client torch recipe gives " + count + ", expected " + expected);
                    return;
                }
                // a difficulty without datapacks may still reload for script recipes: wait until that is over too
                if (serverReport == null || serverReport.contains("reloading=true") || ticks - at < 40) {
                    if (serverReport != null && ticks - at > 1200) fail("Server still reloading: " + serverReport);
                    serverReport = null;
                    if (ticks % 10 == 0) askServer();
                    return;
                }
                PROOF.add("client torch recipe count: " + count);
                PROOF.add("server after choice: " + serverReport);
                require(serverReport.startsWith("mode=" + CHOICE + " "), "Server difficulty after the choice: " + serverReport);
                require(serverReport.contains("stagesOk=true"), "Player stages: " + serverReport);
                require(serverReport.contains("packsOk=true"), "Datapacks: " + serverReport);
                require(serverReport.contains("torch=" + expected), "Server torch recipe: " + serverReport);
                String line = "World difficulty: " + Difficulties.clientGet(CHOICE).name().resolve("en_us").getString();
                require(CHAT.contains(line), "Chat line '" + line + "' not received; chat: " + CHAT);
                String tag = System.getProperty("pathbound.expectTag", "");
                if (!tag.isEmpty()) require(serverReport.contains(tag), "Tag " + tag + " from afterChoose missing: " + serverReport);
                checkScriptRecipes(serverReport);
                PROOF.add("chat: " + CHAT);
                frame("03-after");
                next("State after the choice verified");
                serverReport = null;
                if (Boolean.getBoolean("pathbound.rejoin")) {
                    step = 30;
                    return;
                }
                questStart = mc.getSingleplayerServer().getTickCount();
                if (!ftbQuests() || Boolean.getBoolean("pathbound.skipQuests")) {
                    PROOF.add("FTB Quests absent or -Dpathbound.skipQuests=true; quest checks skipped");
                    step = 9;
                }
            }
            case 6 -> {
                // Stage tasks are checked by FTB Quests on its own schedule; poll the server, budget in server ticks.
                String want = QuestCheck.expected(CHOICE);
                int serverTicks = mc.getSingleplayerServer().getTickCount() - questStart;
                if (serverReport != null && serverReport.endsWith(want)) {
                    PROOF.add("quests on server after " + serverTicks + " server ticks: " + serverReport);
                    LOG.info("TRQVAL quests {}", serverReport);
                    next("FTB Quests state matches " + CHOICE + " on the server");
                    return;
                }
                if (serverTicks > 200) fail("Quest state after " + serverTicks + " server ticks: " + serverReport + "; expected " + want);
                if (ticks % 5 == 0) {
                    var server = mc.getSingleplayerServer();
                    var id = mc.player.getUUID();
                    server.execute(() -> serverReport = QuestCheck.server(server.getPlayerList().getPlayer(id)));
                }
            }
            case 7 -> {
                // The client copy arrives by FTB Quests' own sync packets.
                String client = QuestCheck.client();
                if (!client.equals(QuestCheck.expected(CHOICE))) {
                    if (ticks - at > 200) fail("Client quest state: " + client + "; expected " + QuestCheck.expected(CHOICE));
                    return;
                }
                PROOF.add("quests on client: " + client);
                QuestCheck.openBook();
                next("Client quest data matches; opened the quest book on trq_mode_test");
            }
            case 8 -> {
                if (ticks - at < 40) return;
                frame("04-quests");
                next("Quest book frame saved (" + mc.screen + ")");
            }
            case 9 -> {
                if (!Boolean.getBoolean("pathbound.sizes")) {
                    step = 13;
                    return;
                }
                mc.setScreen(new ChooserScreen(true));
                size = 0;
                next("Chooser reopened for size frames");
            }
            case 10 -> {
                // The chooser at other window sizes, GUI scales and in Russian, for review of the layout.
                if (size == SIZES.length) {
                    next("Chooser frames at " + SIZES.length + " sizes saved");
                    mc.setScreen(null);
                    step = 13;
                    return;
                }
                int[] cfg = SIZES[size];
                String lang = cfg[3] == 1 ? "ru_ru" : "en_us";
                if (sizeTick == 0) {
                    if (!mc.getLanguageManager().getSelected().equals(lang)) {
                        mc.getLanguageManager().setSelected(lang);
                        reloading = mc.reloadResourcePacks();
                    }
                    GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), cfg[0], cfg[1]);
                    mc.options.guiScale().set(cfg[2]);
                    mc.resizeDisplay();
                }
                if (reloading != null && !reloading.isDone() || mc.getOverlay() != null || ++sizeTick < 20) return;
                reloading = null;
                if (!(mc.screen instanceof ChooserScreen)) {
                    // a resource reload may have closed it; give it a few rendered frames before the capture
                    mc.setScreen(new ChooserScreen(true));
                    sizeTick = 10;
                    return;
                }
                var w = mc.getWindow();
                String name = "05-chooser-" + cfg[0] + "x" + cfg[1] + "-gs" + cfg[2] + (cfg[3] == 1 ? "-ru" : "");
                frame(name);
                PROOF.add(name + ": framebuffer " + w.getWidth() + "x" + w.getHeight() + ", gui " + w.getGuiScaledWidth() + "x" + w.getGuiScaledHeight());
                size++;
                sizeTick = 0;
            }
            case 11 -> {
                // The host of a world without cheats clears the choice with /pathbound choose; the chooser must come back.
                if (!Boolean.getBoolean("pathbound.testChoose")) {
                    step = 99;
                    return;
                }
                if (mc.screen != null) mc.setScreen(null);
                require(!mc.player.hasPermissions(2), "The validation world should not allow cheats");
                mc.player.connection.sendCommand("pathbound choose");
                next("Sent /pathbound choose as the world owner without cheats");
            }
            case 12 -> {
                if (!(mc.screen instanceof ChooserScreen) || !ClientLogic.mode.equals(Difficulty.NONE)) {
                    if (ticks - at > 400) fail("/pathbound choose did not bring the chooser back; screen " + mc.screen + ", difficulty " + ClientLogic.mode);
                    return;
                }
                if (ticks - at < 40) return;
                frame("06-choose-again");
                PROOF.add("chat: " + CHAT);
                next("/pathbound choose cleared the difficulty and the chooser is back");
                step = 99;
            }
            case 13 -> {
                // Frames of the animations: the intro just after opening and when it is over, a hover at its start and end.
                if (!Boolean.getBoolean("pathbound.animFrames")) {
                    step = 11;
                    return;
                }
                animScreen = new ChooserScreen(true);
                mc.setScreen(animScreen);
                next("Chooser reopened for animation frames");
            }
            case 14 -> {
                if (ticks - at == 3) frame("07-intro-mid");
                if (ticks - at < 30) return;
                frame("07-intro-end");
                animScreen.forceHover(CHOICE);
                next("Intro frames saved; hovering " + CHOICE);
            }
            case 15 -> {
                if (ticks - at == 1) {
                    frame("07-hover-start");
                    PROOF.add("lift after 1 tick: " + String.format("%.2f", animScreen.liftOf(CHOICE)));
                }
                if (ticks - at < 12) return;
                frame("07-hover-end");
                double lift = animScreen.liftOf(CHOICE);
                PROOF.add("lift after 12 ticks: " + String.format("%.2f", lift));
                require(lift > 3, "The hovered card did not rise: lift " + lift);
                animScreen.forceHover(null);
                mc.setScreen(null);
                next("Hover lift frames saved");
                step = 11;
            }
            case 30 -> {
                // Leave and reopen the world: the first data load of a world runs before the world exists, so script
                // recipes that depend on the difficulty need the reload Pathbound starts once the world is up.
                mc.level.disconnect();
                mc.disconnect(new net.minecraft.client.gui.screens.GenericMessageScreen(net.minecraft.network.chat.Component.literal("Saving")));
                next("Left the world to reopen it");
            }
            case 31 -> {
                if (mc.level != null || mc.getSingleplayerServer() != null) return;
                mc.createWorldOpenFlows().openWorld(levelId, () -> fail("Reopening " + levelId + " was cancelled"));
                next("Reopening " + levelId);
            }
            case 32 -> {
                if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
                if (!ClientLogic.mode.equals(CHOICE)) {
                    if (ticks - at > 1200) fail("After reopening the server reports " + ClientLogic.mode);
                    return;
                }
                require(!(mc.screen instanceof ChooserScreen), "The chooser opened in a world that has a difficulty");
                if (serverReport == null || serverReport.contains("reloading=true")) {
                    serverReport = null;
                    if (ticks - at > 1200) fail("No settled server report after reopening");
                    if (ticks % 10 == 0) askServer();
                    return;
                }
                if (ticks - at < 60) return;
                PROOF.add("server after reopening: " + serverReport);
                checkScriptRecipes(serverReport);
                serverReport = null;
                frame("05-reopened");
                next("Reopened the world; difficulty, packs and script recipes still match");
                step = 9;   // the quest checks belong to the first join; they ran in runs without rejoin
            }
            default -> {
                write("PASS");
                done = true;
                mc.stop();
            }
        }
    }

    /** Checked without touching QuestCheck, whose FTB types must not load when FTB Quests is absent. */
    private static boolean ftbQuests() {
        try {
            Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static void next(String note) {
        LOG.info("TRQVAL step {} at tick {}: {}", step, ticks, note);
        PROOF.add("tick " + ticks + ": " + note);
        step++;
        at = ticks;
    }

    private static int clientTorchCount(Minecraft mc) {
        if (mc.getConnection() == null) return -1;
        return mc.getConnection().getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("torch"))
                .map(r -> r.value().getResultItem(mc.level.registryAccess()).getCount()).orElse(-1);
    }

    /** Collects the server's view on its own thread; the client reads it on a later tick. */
    private static void askServer() {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            String mode = DifficultyData.get(server).mode();
            Difficulty d = Difficulties.get(mode);
            var tags = player.getTags().stream().sorted().toList();
            var selected = server.getPackRepository().getSelectedIds();
            boolean stagesOk = d == null || tags.containsAll(d.stages());
            boolean packsOk = d == null || d.datapacks().stream().allMatch(p -> selected.contains(ServerLogic.packId(p)));
            for (Difficulty other : Difficulties.all()) {
                if (d == null || other == d) continue;
                for (String s : other.stages()) if (!d.stages().contains(s) && tags.contains(s)) stagesOk = false;
                for (String p : other.datapacks()) if (!d.datapacks().contains(p) && selected.contains(ServerLogic.packId(p))) packsOk = false;
            }
            int torch = server.getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("torch"))
                    .map(r -> r.value().getResultItem(server.registryAccess()).getCount()).orElse(-1);
            serverReport = "mode=" + mode + " tags=" + tags + " stagesOk=" + stagesOk + " packsOk=" + packsOk
                    + " packs=" + selected.stream().filter(p -> p.contains("pathbound")).toList() + " torch=" + torch + " reloading=" + ServerLogic.reloadPending()
                    + " kjs=" + server.getRecipeManager().getRecipes().stream().map(r -> r.id().toString()).filter(rid -> rid.startsWith("pathbound_test:")).sorted().toList();
        });
    }

    /**
     * -Dpathbound.expectRecipes=a:b,c:d must be in the server's recipes, -Dpathbound.expectNoRecipes=... must not
     * (recipes from PathboundEvents.recipes in the test scripts, namespace pathbound_test).
     */
    private static void checkScriptRecipes(String report) {
        for (String id : System.getProperty("pathbound.expectRecipes", "").split(",")) {
            if (!id.isBlank()) require(report.contains(id.trim()), "Script recipe " + id + " missing: " + report);
        }
        for (String id : System.getProperty("pathbound.expectNoRecipes", "").split(",")) {
            if (!id.isBlank()) require(!report.contains(id.trim()), "Script recipe " + id + " of another difficulty present: " + report);
        }
    }

    private static Path output() {        return Path.of(System.getProperty("pathbound.validationOutput", "build/ci/difficulty"));
    }

    private static void frame(String name) {
        try {
            Files.createDirectories(output());
            try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                image.writeToFile(output().resolve(name + ".png"));
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(String verdict) {
        try {
            Files.createDirectories(output());
            Files.writeString(output().resolve("proof.txt"), verdict + " (" + CHOICE + ")\n" + String.join("\n", PROOF) + "\n");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) fail(message);
    }

    private static void fail(String message) {
        PROOF.add("FAIL: " + message);
        try {
            frame("fail");
        } catch (RuntimeException ignored) {
        }
        write("FAIL");
        done = true;
        throw new IllegalStateException(message);
    }
}

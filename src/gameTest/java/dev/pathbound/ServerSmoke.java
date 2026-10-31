package dev.pathbound;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * runServerCheck (-Dpathbound.serverSmoke=true): a dedicated server starts with the mod, runs /pathbound commands as the console
 * would, waits for the difficulty's datapacks, writes smoke.txt in the game directory and stops. Dev-only.
 * -Dpathbound.smokeExpectMode=trial: the world must already have that difficulty at start (defaultMode check).
 */
@EventBusSubscriber(modid = Pathbound.MODID)
public final class ServerSmoke {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String HARD_PACK = "pathbound/config/test_trial";
    private static final List<String> PROOF = new ArrayList<>();
    private static int wait = -1;

    private ServerSmoke() {
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("pathbound.serverSmoke")) return;
        MinecraftServer server = event.getServer();
        var repo = server.getPackRepository();
        note("dist=" + FMLEnvironment.dist + " dedicated=" + server.isDedicatedServer());
        note("difficulties=" + Difficulties.all().stream().map(Difficulty::id).toList());
        note("difficulties.json exists=" + Files.exists(Difficulties.configFile())
                + " server.toml exists=" + Files.exists(FMLPaths.CONFIGDIR.get().resolve("pathbound-server.toml")));
        note("pathbound packs available=" + repo.getAvailableIds().stream().filter(p -> p.contains("pathbound")).toList()
                + " selected=" + repo.getSelectedIds().stream().filter(p -> p.contains("pathbound")).toList());
        note("kubejs loaded=" + net.neoforged.fml.ModList.get().isLoaded("kubejs") + " ftbquests loaded=" + net.neoforged.fml.ModList.get().isLoaded("ftbquests"));
        String start = DifficultyData.get(server).mode();
        note("mode at start=" + start);
        String expect = System.getProperty("pathbound.smokeExpectMode", "");
        if (!expect.isEmpty() && !start.equals(expect)) {
            finish(server, "FAIL: expected " + expect + " at start (defaultMode), got " + start);
            return;
        }
        console(server, "pathbound mode");
        console(server, "pathbound list");
        if (expect.isEmpty()) console(server, "pathbound mode set trial");
        wait = 0;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (wait < 0) return;
        MinecraftServer server = event.getServer();
        boolean on = server.getPackRepository().getSelectedIds().contains(HARD_PACK);
        if (!ServerLogic.reloadPending() && on && DifficultyData.get(server).mode().equals("trial")) {
            int torch = server.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.withDefaultNamespace("torch"))
                    .map(r -> r.value().getResultItem(server.registryAccess()).getCount()).orElse(-1);
            note("after " + wait + " ticks: mode=trial, pack selected, torch=" + torch);
            console(server, "pathbound mode");
            finish(server, torch == 2 ? "PASS" : "FAIL: torch recipe gives " + torch);
        } else if (++wait > 600) {
            finish(server, "FAIL: after 600 ticks mode=" + DifficultyData.get(server).mode() + " packSelected=" + on);
        }
    }

    private static void console(MinecraftServer server, String command) {
        note("> /" + command);
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    private static void note(String s) {
        LOG.info("PATHBOUND_SMOKE {}", s);
        PROOF.add(s);
    }

    private static void finish(MinecraftServer server, String verdict) {
        wait = -1;
        note(verdict);
        try {
            Files.writeString(Path.of("smoke.txt"), verdict + "\n" + String.join("\n", PROOF) + "\n");
        } catch (java.io.IOException e) {
            LOG.error("Cannot write smoke.txt", e);
        }
        server.halt(false);
    }
}

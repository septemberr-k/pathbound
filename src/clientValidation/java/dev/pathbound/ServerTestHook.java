package dev.pathbound;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * runServerTest (-Dpathbound.serverTest=true): the dedicated server side of the network check. The first player joins
 * without permissions; after a while the "console" runs op for them (the chooser must open on their client without a
 * rejoin); once the world has a difficulty and the data reload is over, the server's view goes to server-proof.txt.
 * When the player leaves, the server stops. The client side is DifficultyValidation with -Dpathbound.connect=host:port.
 */
@EventBusSubscriber(modid = Pathbound.MODID)
public final class ServerTestHook {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ON = Boolean.getBoolean("pathbound.serverTest");
    private static final List<String> PROOF = new ArrayList<>();
    private static ServerPlayer player;
    private static int joinedAt = -1;
    private static boolean opped, reported;

    private ServerTestHook() {
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!ON) return;
        MinecraftServer server = event.getServer();
        note("dedicated=" + server.isDedicatedServer() + " difficulties=" + Difficulties.all().stream().map(Difficulty::id).toList()
                + " choosePermission=" + Config.choosePermission() + " mode at start=" + DifficultyData.get(server).mode());
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ON || player != null || !(event.getEntity() instanceof ServerPlayer p)) return;
        player = p;
        joinedAt = p.server.getTickCount();
        note("joined: " + p.getGameProfile().getName() + " permissionLevel=" + p.server.getProfilePermissions(p.getGameProfile())
                + " canChoose=" + ServerLogic.canChoose(p));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (!ON || player == null) return;
        MinecraftServer server = event.getServer();
        // give the client time to show (and photograph) the waiting notice, then op the player like an admin would
        if (!opped && server.getTickCount() - joinedAt > 200) {
            opped = true;
            String name = player.getGameProfile().getName();
            note("console: op " + name);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "op " + name);
        }
        DifficultyData data = DifficultyData.get(server);
        if (!reported && data.chosen() && !ServerLogic.reloadPending()) {
            reported = true;
            var selected = server.getPackRepository().getSelectedIds();
            int torch = server.getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("torch"))
                    .map(r -> r.value().getResultItem(server.registryAccess()).getCount()).orElse(-1);
            note("after choice: mode=" + data.mode() + " tags=" + player.getTags().stream().sorted().toList()
                    + " packs=" + selected.stream().filter(s -> s.contains("pathbound")).toList() + " torch=" + torch
                    + " kjs=" + server.getRecipeManager().getRecipes().stream().map(r -> r.id().toString()).filter(id -> id.startsWith("pathbound_test:")).sorted().toList());
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!ON || event.getEntity() != player) return;
        note("left: " + player.getGameProfile().getName() + "; stopping");
        write();
        player.server.halt(false);
    }

    private static void note(String s) {
        LOG.info("PATHBOUND_SERVERTEST {}", s);
        PROOF.add(s);
    }

    private static void write() {
        try {
            Files.writeString(Path.of("server-proof.txt"), String.join("\n", PROOF) + "\n");
        } catch (java.io.IOException e) {
            LOG.error("Cannot write server-proof.txt", e);
        }
    }
}

package dev.pathbound;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client side: keeps the chooser on screen until the server reports a difficulty. */
public final class ClientLogic {
    private static boolean pending;
    private static boolean canChoose;
    /** Last difficulty id the server reported for this world ("none" while undecided). */
    static String mode = Difficulty.NONE;
    /** Set after the host clicks, so the chooser does not reopen while the server reloads. */
    static boolean submitted;

    private ClientLogic() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener(ClientLogic::onRegisterReloadListeners);
        NeoForge.EVENT_BUS.addListener(ClientLogic::onTick);
        NeoForge.EVENT_BUS.addListener(ClientLogic::onLogout);
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(ChooserLayout.RELOADER);
    }

    static void onList(DifficultyListPayload payload) {
        List<Difficulty> list = new ArrayList<>();
        for (JsonElement e : JsonParser.parseString(payload.json()).getAsJsonArray()) list.add(Difficulty.fromClientJson(e.getAsJsonObject()));
        Difficulties.setClient(List.copyOf(list));
    }

    static void onModeState(ModeStatePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mode = payload.mode();
        pending = mode.equals(Difficulty.NONE);
        canChoose = payload.canChoose();
        if (!pending) {
            submitted = false;
            if (mc.screen instanceof ChooserScreen) mc.setScreen(null);
        } else if (!(mc.screen instanceof ChooserScreen open) || open.canChoose() != canChoose) {
            // Also after a refused choice (the server answers with "none" and the chooser comes back), and after /op or
            // /deop: the waiting notice turns into the chooser, or back.
            submitted = false;
            mc.setScreen(new ChooserScreen(canChoose));
        }
    }

    // Other mods may open their own screen on join (guide books, toasts); bring the chooser back afterwards.
    // Only for the player who can choose: on a server with no operator online the others would be locked out.
    private static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (pending && canChoose && !submitted && mc.screen == null && mc.player != null) {
            mc.setScreen(new ChooserScreen(canChoose));
        }
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        pending = false;
        submitted = false;
        mode = Difficulty.NONE;
        Difficulties.setClient(List.of());
    }
}

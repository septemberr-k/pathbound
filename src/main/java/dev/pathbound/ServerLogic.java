package dev.pathbound;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

public final class ServerLogic {
    private static final Logger LOG = LogUtils.getLogger();
    static final String BUILTIN = "builtin:";
    static final String CONFIG = "config:";

    private ServerLogic() {
    }

    /** The host of a singleplayer / LAN world, or an operator on a dedicated server. */
    static boolean canChoose(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server != null && (server.isSingleplayerOwner(player.getGameProfile()) || player.hasPermissions(2));
    }

    @SubscribeEvent
    public static void onAboutToStart(ServerAboutToStartEvent event) {
        Difficulties.load();
    }

    /** Keeps the datapacks in step with the stored difficulty, e.g. after someone toggled one by hand. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        start(event.getServer());
    }

    /** Applies the configured default to a world without a difficulty, then brings the datapacks in line. */
    static void start(MinecraftServer server) {
        DifficultyData data = DifficultyData.get(server);
        String configured = Config.defaultMode();
        if (!data.chosen() && !configured.equals(Difficulty.NONE)) {
            if (Difficulties.get(configured) == null) {
                LOG.error("defaultMode = {} in pathbound-server.toml is not a known difficulty {}", configured, ids());
            } else {
                LOG.info("No world difficulty yet; applying defaultMode = {} from the server config", configured);
                setMode(server, configured, null);
                return;
            }
        }
        if (!data.chosen() && Difficulties.all().stream().noneMatch(Difficulty::selectable)) {
            LOG.warn("No selectable difficulty and no defaultMode: nobody can choose one; set one with /pathbound mode set <id>");
        }
        if (data.chosen() && Difficulties.get(data.mode()) == null) {
            LOG.warn("This world's difficulty '{}' is not defined any more {}; its stages and datapacks are not applied", data.mode(), ids());
        }
        syncPacks(server, data.mode());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String mode = DifficultyData.get(player.server).mode();
        applyStages(player, mode);
        sendList(player);
        sendState(player, mode);
        Difficulty d = Difficulties.get(mode);
        if (d != null) Hooks.playerJoin.joined(player, d);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("pathbound")
                .then(Commands.literal("mode")
                        .executes(ctx -> {
                            String mode = DifficultyData.get(ctx.getSource().getServer()).mode();
                            ctx.getSource().sendSuccess(() -> Component.translatable("pathbound.cmd.current", modeName(mode)), false);
                            return 1;
                        })
                        .then(Commands.literal("set").requires(ServerLogic::mayChange)
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(suggestions(), b))
                                        .executes(ctx -> {
                                            String mode = Difficulties.canonical(StringArgumentType.getString(ctx, "mode"));
                                            if (!mode.equals(Difficulty.NONE) && Difficulties.get(mode) == null) {
                                                ctx.getSource().sendFailure(Component.translatable("pathbound.cmd.unknown", mode, String.join(", ", suggestions())));
                                                return 0;
                                            }
                                            setMode(ctx.getSource().getServer(), mode, null);
                                            return 1;
                                        }))))
                // Clears the choice and shows the chooser again to the host / operators online.
                .then(Commands.literal("choose").requires(ServerLogic::mayChange).executes(ctx -> {
                    setMode(ctx.getSource().getServer(), Difficulty.NONE, null);
                    ctx.getSource().sendSuccess(() -> Component.translatable("pathbound.cmd.choose"), true);
                    return 1;
                }))
                .then(Commands.literal("list").executes(ctx -> {
                    for (Difficulty df : Difficulties.all()) {
                        ctx.getSource().sendSuccess(() -> Component.literal(df.id() + " - ").append(df.name().server())
                                .append(Component.literal(" stages=" + df.stages() + " datapacks=" + df.datapacks() + (df.selectable() ? "" : " (not selectable)"))), false);
                    }
                    return Difficulties.all().size();
                })));
    }

    /** Operators, and the owner of a singleplayer world even without cheats. */
    static boolean mayChange(CommandSourceStack src) {
        if (src.hasPermission(2)) return true;
        ServerPlayer p = src.getPlayer();
        return p != null && src.getServer().isSingleplayerOwner(p.getGameProfile());
    }

    private static List<String> suggestions() {
        List<String> s = new ArrayList<>(ids());
        s.add(Difficulty.NONE);
        return s;
    }

    private static List<String> ids() {
        return Difficulties.all().stream().map(Difficulty::id).toList();
    }

    static void onChoose(ChooseModePayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        DifficultyData data = DifficultyData.get(player.server);
        Difficulty wanted = Difficulties.get(payload.mode());
        if (data.chosen() || wanted == null || !wanted.selectable() || !canChoose(player)) {
            // A late, repeated or forged request: tell the client how things stand.
            sendState(player, data.mode());
            return;
        }
        if (!Hooks.beforeChoose.allow(player, wanted)) {
            LOG.info("A script refused {} choosing {}", player.getGameProfile().getName(), wanted.id());
            player.sendSystemMessage(Component.translatable("pathbound.refused", wanted.name().server()));
            sendState(player, data.mode());
            return;
        }
        LOG.info("{} chose world difficulty {}", player.getGameProfile().getName(), wanted.id());
        setMode(player.server, wanted.id(), player);
    }

    /** Stores the difficulty and applies it to everyone online; offline players get their stages when they join. */
    static void setMode(MinecraftServer server, String mode, ServerPlayer chooser) {
        DifficultyData.get(server).setMode(mode);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            applyStages(p, mode);
            sendState(p, mode);
        }
        Difficulty d = Difficulties.get(mode);
        if (d != null) {
            server.getPlayerList().broadcastSystemMessage(Component.translatable("pathbound.chosen", d.name().server()), false);
        }
        syncPacks(server, mode);
        if (d != null) {
            // Commands may need the difficulty's datapacks (functions, recipes), so they wait for the reload.
            afterReload(server, () -> {
                // Skip them if the difficulty was changed again while the data reloaded.
                if (DifficultyData.get(server).mode().equals(d.id())) runCommands(server, d);
            });
            Hooks.afterChoose.chosen(server, chooser, d);
        }
    }

    private static void runCommands(MinecraftServer server, Difficulty d) {
        CommandSourceStack source = server.createCommandSourceStack();
        for (String command : d.commands()) {
            LOG.info("Difficulty {}: /{}", d.id(), command);
            server.getCommands().performPrefixedCommand(source, command);
        }
    }

    static Component modeName(String mode) {
        Difficulty d = Difficulties.get(mode);
        if (d != null) return d.name().server();
        return mode.equals(Difficulty.NONE) ? Component.translatable("pathbound.mode.none") : Component.literal(mode);
    }

    /** Player tags double as KubeJS stages, which FTB Quests stage tasks read. */
    static void applyStages(ServerPlayer player, String mode) {
        Difficulty chosen = Difficulties.get(mode);
        Set<String> keep = chosen == null ? Set.of() : Set.copyOf(chosen.stages());
        for (Difficulty d : Difficulties.all()) {
            for (String stage : d.stages()) if (!keep.contains(stage)) player.removeTag(stage);
        }
        for (String stage : keep) player.addTag(stage);
    }

    static void sendList(ServerPlayer player) {
        if (!player.connection.hasChannel(DifficultyListPayload.TYPE)) return;
        PacketDistributor.sendToPlayer(player, new DifficultyListPayload(Difficulty.toClientJson(Difficulties.all()).toString()));
    }

    static void sendState(ServerPlayer player, String mode) {
        // Fake and test players have no mod channel; sending to them throws and takes the server down.
        if (!player.connection.hasChannel(ModeStatePayload.TYPE)) return;
        // With nothing selectable the host could never leave the chooser; show the closable waiting notice instead.
        boolean any = Difficulties.all().stream().anyMatch(Difficulty::selectable);
        PacketDistributor.sendToPlayer(player, new ModeStatePayload(mode, canChoose(player) && any));
    }

    /**
     * "builtin:name" is datapacks/name inside this mod's jar, "config:name" is config/pathbound/datapacks/name (a
     * folder or name.zip); anything else is a pack id as is.
     */
    static String packId(String spec) {
        if (spec.startsWith(BUILTIN)) return "mod/" + Pathbound.MODID + ":datapacks/" + spec.substring(BUILTIN.length());
        if (spec.startsWith(CONFIG)) return Pathbound.MODID + "/config/" + spec.substring(CONFIG.length());
        return spec;
    }

    /** The data reload this mod started last and its server; the selected packs only change once it finishes. */
    private static CompletableFuture<Void> reload = CompletableFuture.completedFuture(null);
    private static MinecraftServer reloadServer;

    static boolean reloadPending() {
        return !reload.isDone();
    }

    private static void afterReload(MinecraftServer server, Runnable task) {
        if (reloadServer == server && reloadPending()) reload.thenRunAsync(task, server);
        else task.run();
    }

    /** Enables the chosen difficulty's datapacks, disables every other difficulty's, and reloads data if that changed. */
    static void syncPacks(MinecraftServer server, String mode) {
        // A reload left behind by a world that was closed (singleplayer) never concerns this server.
        if (reloadServer == server && reloadPending()) {
            // Deciding now would compare against the old selection; check again with the difficulty stored by then.
            reload.whenCompleteAsync((ok, error) -> syncPacks(server, DifficultyData.get(server).mode()), server);
            return;
        }
        PackRepository repo = server.getPackRepository();
        Difficulty chosen = Difficulties.get(mode);
        Set<String> wanted = new LinkedHashSet<>();
        if (chosen != null) chosen.datapacks().forEach(p -> wanted.add(packId(p)));
        Set<String> managed = new LinkedHashSet<>(wanted);
        for (Difficulty d : Difficulties.all()) d.datapacks().forEach(p -> managed.add(packId(p)));

        List<Pack> selected = new ArrayList<>(repo.getSelectedPacks());
        boolean changed = selected.removeIf(p -> managed.contains(p.getId()) && !wanted.contains(p.getId()));
        for (String id : wanted) {
            Pack pack = repo.getPack(id);
            if (pack == null) {
                LOG.warn("Datapack {} of difficulty {} not found; available packs: {}", id, mode, repo.getAvailableIds());
                continue;
            }
            if (selected.contains(pack)) continue;
            pack.getDefaultPosition().insert(selected, pack, Pack::selectionConfig, false);
            changed = true;
        }
        if (!changed) return;
        List<String> ids = selected.stream().map(Pack::getId).toList();
        LOG.info("Difficulty {}: datapacks now {}", mode, ids.stream().filter(managed::contains).toList());
        reloadServer = server;
        reload = server.reloadResources(ids).exceptionally(error -> {
            LOG.warn("Failed to reload data packs", error);
            return null;
        });
    }
}

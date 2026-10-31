package dev.pathbound;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

/** Pathbound: a per-world difficulty chosen once, on first join; the difficulties are data. */
@Mod(Pathbound.MODID)
public final class Pathbound {
    public static final String MODID = "pathbound";
    private static final Logger LOG = LogUtils.getLogger();
    private static ModContainer container;

    public Pathbound(IEventBus modBus, ModContainer container) {
        Pathbound.container = container;
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        modBus.addListener(Pathbound::registerPayloads);
        modBus.addListener(Pathbound::addPacks);
        modBus.addListener(Pathbound::addConfigPacks);
        NeoForge.EVENT_BUS.register(ServerLogic.class);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientLogic.init(modBus);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // The client handlers live in ClientLogic; the lambdas keep that class unloaded on a dedicated server.
        registrar.playToClient(DifficultyListPayload.TYPE, DifficultyListPayload.CODEC, (payload, ctx) -> ClientLogic.onList(payload));
        registrar.playToClient(ModeStatePayload.TYPE, ModeStatePayload.CODEC, (payload, ctx) -> ClientLogic.onModeState(payload));
        registrar.playToServer(ChooseModePayload.TYPE, ChooseModePayload.CODEC, ServerLogic::onChoose);
    }

    /**
     * Every folder datapacks/NAME (with a pack.mcmeta) in the mod jar becomes the pack "builtin:NAME" for
     * difficulties.json. They are never enabled on their own (FEATURE source); ServerLogic switches them.
     */
    private static void addPacks(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;
        Path root = container.getModInfo().getOwningFile().getFile().findResource("datapacks");
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> dirs = Files.list(root)) {
            dirs.filter(d -> Files.exists(d.resolve("pack.mcmeta"))).map(d -> d.getFileName().toString().replace("/", "")).sorted().forEach(name ->
                    event.addPackFinders(id("datapacks/" + name), PackType.SERVER_DATA,
                            Component.literal("Difficulty pack: " + name), PackSource.FEATURE, false, Pack.Position.TOP));
        } catch (IOException e) {
            LOG.error("Cannot list the built-in difficulty datapacks", e);
        }
    }

    /**
     * Every folder or .zip in config/pathbound/datapacks becomes the pack "config:NAME" for difficulties.json, so a
     * modpack ships its difficulty datapacks without a jar of its own. Like the built-in ones, never enabled by itself.
     */
    private static void addConfigPacks(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;
        event.addRepositorySource(onLoad -> {
            Path dir = Difficulties.configDir().resolve("datapacks");
            if (!Files.isDirectory(dir)) return;
            try (Stream<Path> entries = Files.list(dir)) {
                entries.sorted().forEach(p -> {
                    String name = p.getFileName().toString();
                    Pack.ResourcesSupplier resources;
                    if (Files.isDirectory(p)) resources = new PathPackResources.PathResourcesSupplier(p);
                    else if (name.endsWith(".zip")) resources = new FilePackResources.FileResourcesSupplier(p);
                    else return;
                    var info = new PackLocationInfo(ServerLogic.packId(ServerLogic.CONFIG + name), Component.literal("Difficulty pack: " + name), PackSource.FEATURE, Optional.empty());
                    Pack pack = Pack.readMetaAndCreate(info, resources, PackType.SERVER_DATA, new PackSelectionConfig(false, Pack.Position.TOP, false));
                    if (pack != null) onLoad.accept(pack);
                    else LOG.warn("{} is not a datapack (no valid pack.mcmeta)", p);
                });
            } catch (IOException e) {
                LOG.error("Cannot list {}", dir, e);
            }
        });
    }
}

package dev.pathbound.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.EventTargetType;
import dev.latvian.mods.kubejs.event.TargetedEventHandler;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.plugin.builtin.event.ServerEvents;
import dev.latvian.mods.kubejs.recipe.RecipesKubeEvent;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.pathbound.DifficultyApi;
import dev.pathbound.Hooks;

/**
 * KubeJS integration, listed in kubejs.plugins.txt so only KubeJS ever loads it; nothing else in the mod refers to
 * this package, so the mod runs the same without KubeJS.
 * <pre>
 * PathboundEvents.register(e => ...)          startup: add / modify / remove difficulties
 * PathboundEvents.recipes('trial', e => ...)  server: recipes for worlds with that difficulty (e is ServerEvents.recipes' event)
 * PathboundEvents.beforeChoose(e => ...)      server: e.player, e.difficulty; e.cancel() refuses the choice
 * PathboundEvents.afterChoose(e => ...)       server: e.player (null for config/command), e.difficulty, e.server
 * PathboundEvents.playerJoin(e => ...)        server: e.player, e.difficulty
 * Pathbound.get(server) / current() / set(server, id) / ids() / info(id)
 * </pre>
 */
public final class PathboundKubeJSPlugin implements KubeJSPlugin {
    public static final EventGroup GROUP = EventGroup.of("PathboundEvents");
    public static final EventHandler REGISTER = GROUP.startup("register", () -> RegisterEventJS.class);
    public static final TargetedEventHandler<String> RECIPES = GROUP.server("recipes", () -> RecipesKubeEvent.class).requiredTarget(EventTargetType.STRING);
    public static final EventHandler BEFORE_CHOOSE = GROUP.server("beforeChoose", () -> ChooseEventJS.class).hasResult();
    public static final EventHandler AFTER_CHOOSE = GROUP.server("afterChoose", () -> ChosenEventJS.class);
    public static final EventHandler PLAYER_JOIN = GROUP.server("playerJoin", () -> ChooseEventJS.class);

    /** The difficulty the recipes were last built for; null when they were built without one (the first load of a world). */
    private static volatile String recipesBuiltFor;

    @Override
    public void init() {
        var register = Hooks.register;
        Hooks.register = map -> {
            register.accept(map);
            if (REGISTER.hasListeners()) REGISTER.post(ScriptType.STARTUP, new RegisterEventJS(map));
        };
        var before = Hooks.beforeChoose;
        Hooks.beforeChoose = (player, d) -> before.allow(player, d)
                && !(BEFORE_CHOOSE.hasListeners() && BEFORE_CHOOSE.post(ScriptType.SERVER, new ChooseEventJS(player, d)).interruptFalse());
        var after = Hooks.afterChoose;
        Hooks.afterChoose = (server, player, d) -> {
            after.chosen(server, player, d);
            if (AFTER_CHOOSE.hasListeners()) AFTER_CHOOSE.post(ScriptType.SERVER, new ChosenEventJS(server, player, d));
        };
        var join = Hooks.playerJoin;
        Hooks.playerJoin = (player, d) -> {
            join.joined(player, d);
            if (PLAYER_JOIN.hasListeners()) PLAYER_JOIN.post(ScriptType.SERVER, new ChooseEventJS(player, d));
        };
        // Recipes only change with a data reload: ask for one when the recipes were built for another difficulty.
        var reload = Hooks.needsDataReload;
        Hooks.needsDataReload = mode -> reload.test(mode) || !recipeTarget(mode).equals(recipeTarget(recipesBuiltFor));
    }

    /** The difficulty whose recipe scripts apply for this mode, or "" when none of them has any. */
    private static String recipeTarget(String mode) {
        return mode != null && RECIPES.hasListeners(mode) ? mode : "";
    }

    /**
     * Server scripts are reloaded with every data reload and their handlers cleared; after each load, hook the world's
     * difficulty into the recipe event again. The first load of a world runs before the world exists, so the
     * difficulty is unknown then; the mod reloads once the world has started if the recipes depend on it.
     */
    @Override
    public void afterScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType != ScriptType.SERVER) return;
        ServerEvents.RECIPES.listenJava(ScriptType.SERVER, null, event -> {
            String mode = DifficultyApi.currentOrNull();
            recipesBuiltFor = mode;
            if (mode != null && RECIPES.hasListeners(mode)) RECIPES.post(ScriptType.SERVER, mode, event);
            return null;
        });
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("Pathbound", PathboundBinding.class);
    }
}

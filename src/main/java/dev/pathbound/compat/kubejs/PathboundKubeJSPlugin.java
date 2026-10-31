package dev.pathbound.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.pathbound.Hooks;

/**
 * KubeJS integration, listed in kubejs.plugins.txt so only KubeJS ever loads it; nothing else in the mod refers to
 * this package, so the mod runs the same without KubeJS.
 * <pre>
 * PathboundEvents.register(e => ...)      startup: add / modify / remove difficulties
 * PathboundEvents.beforeChoose(e => ...)  server: e.player, e.difficulty; e.cancel() refuses the choice
 * PathboundEvents.afterChoose(e => ...)   server: e.player (null for config/command), e.difficulty, e.server
 * PathboundEvents.playerJoin(e => ...)    server: e.player, e.difficulty
 * Pathbound.get(server) / set(server, id) / ids() / info(id)
 * </pre>
 */
public final class PathboundKubeJSPlugin implements KubeJSPlugin {
    public static final EventGroup GROUP = EventGroup.of("PathboundEvents");
    public static final EventHandler REGISTER = GROUP.startup("register", () -> RegisterEventJS.class);
    public static final EventHandler BEFORE_CHOOSE = GROUP.server("beforeChoose", () -> ChooseEventJS.class).hasResult();
    public static final EventHandler AFTER_CHOOSE = GROUP.server("afterChoose", () -> ChosenEventJS.class);
    public static final EventHandler PLAYER_JOIN = GROUP.server("playerJoin", () -> ChooseEventJS.class);

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

package dev.pathbound.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.pathbound.Difficulty;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * PathboundEvents.register: runs when a world starts, after config/pathbound/difficulties.json is read.
 * <pre>
 * PathboundEvents.register(e => {
 *   e.add('nightmare', d => d.name({en_us: 'Nightmare', de_de: 'Albtraum'}).color('#FF4040').order(2)
 *     .stage('nightmare').datapack('config:nightmare').command('say Nightmare begins'))
 *   e.modify('hard', d => d.color(0xFF8040))
 *   e.remove('normal')
 * })
 * </pre>
 */
public final class RegisterEventJS implements KubeEvent {
    private final Map<String, Difficulty.Builder> map;

    RegisterEventJS(Map<String, Difficulty.Builder> map) {
        this.map = map;
    }

    /** Adds a difficulty, or replaces one with the same id. */
    public void add(String id, Consumer<Difficulty.Builder> setup) {
        if (!Difficulty.validId(id)) throw new IllegalArgumentException("Bad difficulty id '" + id + "' (a-z, 0-9, _; not 'none')");
        Difficulty.Builder b = new Difficulty.Builder(id);
        setup.accept(b);
        map.put(id, b);
    }

    /** Changes a difficulty from the config or an earlier script; unknown ids are an error. */
    public void modify(String id, Consumer<Difficulty.Builder> change) {
        Difficulty.Builder b = map.get(id);
        if (b == null) throw new IllegalArgumentException("No difficulty '" + id + "' to modify; known: " + map.keySet());
        change.accept(b);
    }

    public void remove(String id) {
        map.remove(id);
    }

    public List<String> getIds() {
        return List.copyOf(map.keySet());
    }
}

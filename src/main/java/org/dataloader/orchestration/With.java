package org.dataloader.orchestration;

import org.dataloader.DataLoader;

import java.util.function.Function;

import static org.dataloader.orchestration.Orchestrator.castAs;
import static org.dataloader.orchestration.Step.loadImpl;

/**
 * A transitional step that allows a new step to be started with a new data loader in play
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public class With<K, V> {
    private final Orchestrator<?, ?> orchestrator;
    private final DataLoader<K, V> dl;

    public With(Orchestrator<?, ?> orchestrator, DataLoader<K, V> dl) {
        this.orchestrator = orchestrator;
        this.dl = dl;
    }

    public Step<K, V> load(K key) {
        return load(key, null);
    }

    public Step<K, V> load(K key, Object keyContext) {
        return loadImpl(orchestrator, castAs(dl), key,keyContext);
    }

    public Step<V, V> thenLoad(Function<V, K> codeToRun) {
        return Step.thenLoadImpl(orchestrator, castAs(dl), codeToRun);
    }
}

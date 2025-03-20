package org.dataloader.orchestration;

import org.dataloader.DataLoader;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.dataloader.orchestration.Orchestrator.castAs;

public class Step<K, V> {
    private final Orchestrator<?, ?> orchestrator;
    private final DataLoader<Object, Object> dl;
    private final Function<K, CompletableFuture<V>> codeToRun;

    Step(Orchestrator<?, ?> orchestrator, DataLoader<?, ?> dataLoader, Function<K, CompletableFuture<V>> codeToRun) {
        this.orchestrator = orchestrator;
        this.dl = castAs(dataLoader);
        this.codeToRun = codeToRun;
    }

    DataLoader<Object, Object> dataLoader() {
        return dl;
    }

    public Function<K, CompletableFuture<V>> codeToRun() {
        return codeToRun;
    }

    public <KT, VT> With<KT, VT> with(DataLoader<KT, VT> dataLoader) {
        return new With<>(orchestrator, dataLoader);
    }

    public Step<K, V> load(K key, Object keyContext) {
        return loadImpl(orchestrator, dl, key, keyContext);
    }

    public Step<V, V> thenLoad(Function<V, K> codeToRun) {
        return thenLoadImpl(orchestrator, dl, codeToRun);
    }

    static <K, V> Step<K, V> loadImpl(Orchestrator<?, ?> orchestrator, DataLoader<Object, Object> dl, K key, Object keyContext) {
        Function<K, CompletableFuture<V>> codeToRun = k -> {
            CompletableFuture<V> cf = castAs(dl.load(key, keyContext));
            orchestrator.getTracker().loadCall(dl);
            return cf;
        };
        Step<K, V> step = new Step<>(orchestrator, dl, codeToRun);
        orchestrator.record(step);
        return step;
    }

    static <K, V> Step<V, V> thenLoadImpl(Orchestrator<?, ?> orchestrator, DataLoader<Object, Object> dl, Function<V, K> codeToRun) {
        Function<V, CompletableFuture<V>> actualCodeToRun = v -> {
            K key = codeToRun.apply(v);
            CompletableFuture<V> cf = castAs(dl.load(key));
            orchestrator.getTracker().loadCall(dl);
            return cf;
        };
        Step<V, V> step = new Step<>(orchestrator, dl, actualCodeToRun);
        orchestrator.record(step);
        return step;
    }

    public CompletableFuture<V> toCompletableFuture() {
        return orchestrator.execute();
    }
}

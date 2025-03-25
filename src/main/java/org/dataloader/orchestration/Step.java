package org.dataloader.orchestration;

import org.dataloader.DataLoader;
import org.dataloader.orchestration.executors.ObservingExecutor;
import org.dataloader.orchestration.observation.Tracker;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
        return thenLoadImpl(orchestrator, dl, codeToRun, false);
    }

    public Step<V, V> thenLoadAsync(Function<V, K> codeToRun) {
        return thenLoadImpl(orchestrator, dl, codeToRun, true);
    }

    static <K, V> Step<K, V> loadImpl(Orchestrator<?, ?> orchestrator, DataLoader<Object, Object> dl, K key, Object keyContext) {
        Tracker tracker = orchestrator.getTracker();
        int stepIndex = tracker.getStepCount();
        Function<K, CompletableFuture<V>> codeToRun = k -> {
            CompletableFuture<V> cf = castAs(dl.load(key, keyContext));
            orchestrator.getTracker().loadCall(stepIndex, dl);
            return cf;
        };
        Step<K, V> step = new Step<>(orchestrator, dl, codeToRun);
        orchestrator.record(step);
        return step;
    }

    static <K, V> Step<V, V> thenLoadImpl(Orchestrator<?, ?> orchestrator, DataLoader<Object, Object> dl, Function<V, K> codeToRun, boolean async) {
        Tracker tracker = orchestrator.getTracker();
        Function<V, CompletableFuture<V>> actualCodeToRun;
        if (async) {
            actualCodeToRun = mkAsyncLoadLambda(orchestrator, dl, codeToRun, tracker);
        } else {
            actualCodeToRun = mkSyncLoadLambda(dl, codeToRun, tracker);
        }
        Step<V, V> step = new Step<>(orchestrator, dl, actualCodeToRun);
        orchestrator.record(step);
        return step;
    }

    private static <K, V> Function<V, CompletableFuture<V>> mkSyncLoadLambda(DataLoader<Object, Object> dl, Function<V, K> codeToRun, Tracker tracker) {
        int stepIndex = tracker.getStepCount();
        return v -> {
            K key = codeToRun.apply(v);
            CompletableFuture<V> cf = castAs(dl.load(key));
            tracker.loadCall(stepIndex, dl);
            return cf;
        };
    }

    private static <K, V> Function<V, CompletableFuture<V>> mkAsyncLoadLambda(Orchestrator<?, ?> orchestrator, DataLoader<Object, Object> dl, Function<V, K> codeToRun, Tracker tracker) {
        int stepIndex = tracker.getStepCount();
        return v -> {
            Executor executor = orchestrator.getExecutor();
            Consumer<String> callback = atSomePointWeNeedMoreStateButUsingStringForNowToMakeItCompile -> {
                tracker.loadCall(stepIndex, dl);
            };
            ObservingExecutor<String> observingExecutor = new ObservingExecutor<>(executor, "state", callback);
            Supplier<CompletableFuture<V>> dataLoaderCall = () -> {
                K key = codeToRun.apply(v);
                return castAs(dl.load(key));
            };
            return CompletableFuture.supplyAsync(dataLoaderCall, observingExecutor)
                    .thenCompose(Function.identity());
        };
    }

    public CompletableFuture<V> toCompletableFuture() {
        return orchestrator.execute();
    }
}

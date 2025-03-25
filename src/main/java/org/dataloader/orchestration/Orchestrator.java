package org.dataloader.orchestration;

import org.dataloader.DataLoader;
import org.dataloader.impl.Assertions;
import org.dataloader.orchestration.executors.ImmediateExecutor;
import org.dataloader.orchestration.observation.Tracker;
import org.dataloader.orchestration.observation.TrackingObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

public class Orchestrator<K, V> {

    private final Executor executor;
    private final Tracker tracker;
    private final DataLoader<K, V> startingDL;
    private final List<Step<?, ?>> steps = new ArrayList<>();

    /**
     * This will create a new {@link Orchestrator} that can allow multiple calls to multiple data-loader's
     * to be orchestrated so they all run optimally.
     *
     * @param dataLoader the data loader to start with
     * @param <K>        the key type
     * @param <V>        the value type
     * @return a new {@link Orchestrator}
     */
    public static <K, V> Builder<K, V> orchestrate(DataLoader<K, V> dataLoader) {
        return new Builder<>(dataLoader);
    }

    public Orchestrator(Builder<K, V> builder) {
        this.tracker = new Tracker(builder.trackingObserver);
        this.executor = builder.executor;
        this.startingDL = builder.dataLoader;
    }

    public Tracker getTracker() {
        return tracker;
    }

    public Executor getExecutor() {
        return executor;
    }


    public Step<K, V> load(K key) {
        return load(key, null);
    }

    public Step<K, V> load(K key, Object keyContext) {
        return Step.loadImpl(this, castAs(startingDL), key, keyContext);
    }

    static <T> T castAs(Object o) {
        //noinspection unchecked
        return (T) o;
    }


    <KT, VT> void record(Step<KT, VT> step) {
        steps.add(step);
        tracker.incrementStepCount();
    }

    /**
     * This is the callback point saying to start the DataLoader loading process.
     * <p>
     * The type of object returned here depends on the value type of the last Step.  We cant be truly generic
     * here and must be case.
     *
     * @param <VT> the value type
     * @return the final composed value
     */
    <VT> CompletableFuture<VT> execute() {
        Assertions.assertState(!steps.isEmpty(), () -> "How can the steps to run be empty??");

        // tell the tracker we are under way
        getTracker().startingExecution();

        int index = 0;
        Step<?, ?> firstStep = steps.get(index);

        CompletableFuture<Object> currentCF = castAs(firstStep.codeToRun().apply(null)); // first load uses variable capture
        whenComplete(index, firstStep, currentCF);

        for (index++; index < steps.size(); index++) {
            Step<?, ?> nextStep = steps.get(index);
            Function<Object, CompletableFuture<?>> codeToRun = castAs(nextStep.codeToRun());
            CompletableFuture<Object> nextCF = currentCF.thenCompose(value -> castAs(codeToRun.apply(value)));
            currentCF = nextCF;

            // side effect when this step is complete
            whenComplete(index, nextStep, nextCF);
        }

        return castAs(currentCF);

    }

    private void whenComplete(int stepIndex, Step<?, ?> step, CompletableFuture<Object> cf) {
        cf.whenComplete((v, throwable) -> {
            if (throwable != null) {
                // TODO - should we be cancelling future steps here - no need for dispatch tracking if they will never run
                System.out.println("A throwable has been thrown on step  " + stepIndex + ": " + throwable.getMessage());
                throwable.printStackTrace(System.out);
            } else {
                System.out.println("step " + stepIndex + " returned : " + v);
            }
            getTracker().loadCallComplete(stepIndex, step.dataLoader(), throwable);
        });
    }


    public static class Builder<K, V> {
        private Executor executor = ImmediateExecutor.INSTANCE;
        private DataLoader<K, V> dataLoader;
        private TrackingObserver trackingObserver;

        Builder(DataLoader<K, V> dataLoader) {
            this.dataLoader = dataLoader;
        }

        public Builder<K, V> executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        public Builder<K, V> observer(TrackingObserver trackingObserver) {
            this.trackingObserver = trackingObserver;
            return this;
        }

        public Orchestrator<K, V> build() {
            return new Orchestrator<>(this);
        }
    }

}

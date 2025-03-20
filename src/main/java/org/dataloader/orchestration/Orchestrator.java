package org.dataloader.orchestration;

import org.dataloader.DataLoader;
import org.dataloader.impl.Assertions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class Orchestrator<K, V> {

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
    public static <K, V> Orchestrator<K, V> orchestrate(DataLoader<K, V> dataLoader) {
        return new Orchestrator<>(new Tracker(), dataLoader);
    }

    public Tracker getTracker() {
        return tracker;
    }

    private Orchestrator(Tracker tracker, DataLoader<K, V> dataLoader) {
        this.tracker = tracker;
        this.startingDL = dataLoader;
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

    private void whenComplete(int index, Step<?, ?> step, CompletableFuture<Object> cf) {
        cf.whenComplete((v, throwable) -> {
            getTracker().loadCallComplete(step.dataLoader());
            // replace with instrumentation code
            if (throwable != null) {
                // TODO - should we be cancelling future steps here - no need for dispatch tracking if they will never run
                System.out.println("A throwable has been thrown on step  " + index + ": " + throwable.getMessage());
                throwable.printStackTrace(System.out);
            } else {
                System.out.println("step " + index + " returned : " + v);
            }
        });
    }


}

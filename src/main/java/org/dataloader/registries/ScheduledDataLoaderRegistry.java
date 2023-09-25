package org.dataloader.registries;

import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.annotations.ExperimentalApi;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.dataloader.impl.Assertions.nonNull;

/**
 * This {@link DataLoaderRegistry} will use a {@link DispatchPredicate} when {@link #dispatchAll()} is called
 * to test (for each {@link DataLoader} in the registry) if a dispatch should proceed.  If the predicate returns false,
 * then a task is scheduled to perform that predicate dispatch again via the {@link ScheduledExecutorService}.
 * <p>
 * In the default mode, when {@link #tickerMode} is false, the registry will continue to loop (test false and reschedule) until such time as the predicate returns true, in which case
 * no rescheduling will occur, and you will need to call dispatch again to restart the process.
 * <p>
 * However, when {@link #tickerMode} is true, the registry will always reschedule continuously after the first ever call to {@link #dispatchAll()}.
 * <p>
 * This will allow you to chain together {@link DataLoader} load calls like this :
 * <pre>{@code
 *   CompletableFuture<String> future = dataLoaderA.load("A")
 *                                          .thenCompose(value -> dataLoaderB.load(value));
 * }</pre>
 * <p>
 * However, it may mean your batching will not be as efficient as it might be. In environments
 * like graphql this might mean you are too eager in fetching.  The {@link DispatchPredicate} still runs to decide if
 * dispatch should happen however in ticker mode it will be continuously rescheduled.
 * <p>
 * When {@link #tickerMode} is true, you really SHOULD close the registry say at the end of a request otherwise you will leave a job
 * on the {@link ScheduledExecutorService} that is continuously dispatching.
 * <p>
 * If you wanted to create a ScheduledDataLoaderRegistry that started a rescheduling immediately, just create one and
 * call {@link #rescheduleNow()}.
 * <p>
 * By default, it uses a {@link Executors#newSingleThreadScheduledExecutor()}} to schedule the tasks.  However, if you
 * are creating a {@link ScheduledDataLoaderRegistry} per request you will want to look at sharing this {@link ScheduledExecutorService}
 * to avoid creating a new thread per registry created.
 * <p>
 * This code is currently marked as {@link ExperimentalApi}
 */
@ExperimentalApi
public class ScheduledDataLoaderRegistry extends DataLoaderRegistry implements AutoCloseable {

    private final ScheduledExecutorService scheduledExecutorService;
    private final DispatchPredicate dispatchPredicate;
    private final Duration schedule;
    private final boolean tickerMode;
    private volatile boolean closed;

    private ScheduledDataLoaderRegistry(Builder builder) {
        this.dataLoaders.putAll(builder.dataLoaders);
        this.scheduledExecutorService = builder.scheduledExecutorService;
        this.dispatchPredicate = builder.dispatchPredicate;
        this.schedule = builder.schedule;
        this.tickerMode = builder.tickerMode;
        this.closed = false;
    }

    /**
     * Once closed this registry will never again reschedule checks
     */
    @Override
    public void close() {
        closed = true;
    }

    /**
     * @return how long the {@link ScheduledExecutorService} task will wait before checking the predicate again
     */
    public Duration getScheduleDuration() {
        return schedule;
    }

    /**
     * @return true of the registry is in ticker mode or false otherwise
     */
    public boolean isTickerMode() {
        return tickerMode;
    }

    @Override
    public void dispatchAll() {
        dispatchAllWithCount();
    }

    @Override
    public int dispatchAllWithCount() {
        int sum = 0;
        for (Map.Entry<String, DataLoader<?, ?>> entry : dataLoaders.entrySet()) {
            DataLoader<?, ?> dataLoader = entry.getValue();
            String key = entry.getKey();
            sum += dispatchOrReschedule(key, dataLoader);
        }
        return sum;
    }

    /**
     * This will immediately dispatch the {@link DataLoader}s in the registry
     * without testing the predicate
     */
    public void dispatchAllImmediately() {
        super.dispatchAll();
    }

    /**
     * This will immediately dispatch the {@link DataLoader}s in the registry
     * without testing the predicate
     *
     * @return total number of entries that were dispatched from registered {@link org.dataloader.DataLoader}s.
     */
    public int dispatchAllWithCountImmediately() {
        return super.dispatchAllWithCount();
    }

    /**
     * This will schedule a task to check the predicate and dispatch if true right now.  It will not do
     * a pre check of the preodicate like {@link #dispatchAll()} would
     */
    public void rescheduleNow() {
        dataLoaders.forEach(this::reschedule);
    }

    private void reschedule(String key, DataLoader<?, ?> dataLoader) {
        if (!closed) {
            Runnable runThis = () -> dispatchOrReschedule(key, dataLoader);
            scheduledExecutorService.schedule(runThis, schedule.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private int dispatchOrReschedule(String key, DataLoader<?, ?> dataLoader) {
        int sum = 0;
        boolean shouldDispatch = dispatchPredicate.test(key, dataLoader);
        if (shouldDispatch) {
            sum = dataLoader.dispatchWithCounts().getKeysCount();
        }
        if (tickerMode || !shouldDispatch) {
            reschedule(key, dataLoader);
        }
        return sum;
    }

    /**
     * By default this will create use a {@link Executors#newSingleThreadScheduledExecutor()}
     * and a schedule duration of 10 milli seconds.
     *
     * @return A builder of {@link ScheduledDataLoaderRegistry}s
     */
    public static Builder newScheduledRegistry() {
        return new Builder();
    }

    public static class Builder {

        private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        private DispatchPredicate dispatchPredicate = (key, dl) -> true;
        private Duration schedule = Duration.ofMillis(10);
        private final Map<String, DataLoader<?, ?>> dataLoaders = new HashMap<>();
        private boolean tickerMode = false;

        public Builder scheduledExecutorService(ScheduledExecutorService executorService) {
            this.scheduledExecutorService = nonNull(executorService);
            return this;
        }

        public Builder schedule(Duration schedule) {
            this.schedule = schedule;
            return this;
        }

        public Builder dispatchPredicate(DispatchPredicate dispatchPredicate) {
            this.dispatchPredicate = nonNull(dispatchPredicate);
            return this;
        }

        /**
         * This will register a new dataloader
         *
         * @param key        the key to put the data loader under
         * @param dataLoader the data loader to register
         *
         * @return this builder for a fluent pattern
         */
        public Builder register(String key, DataLoader<?, ?> dataLoader) {
            dataLoaders.put(key, dataLoader);
            return this;
        }

        /**
         * This will combine together the data loaders in this builder with the ones
         * from a previous {@link DataLoaderRegistry}
         *
         * @param otherRegistry the previous {@link DataLoaderRegistry}
         *
         * @return this builder for a fluent pattern
         */
        public Builder registerAll(DataLoaderRegistry otherRegistry) {
            dataLoaders.putAll(otherRegistry.getDataLoadersMap());
            return this;
        }

        /**
         * This sets ticker mode on the registry.  When ticker mode is true the registry will
         * continuously reschedule the data loaders for possible dispatching after the first call
         * to dispatchAll.
         *
         * @param tickerMode true or false
         *
         * @return this builder for a fluent pattern
         */
        public Builder tickerMode(boolean tickerMode) {
            this.tickerMode = tickerMode;
            return this;
        }

        /**
         * @return the newly built {@link ScheduledDataLoaderRegistry}
         */
        public ScheduledDataLoaderRegistry build() {
            return new ScheduledDataLoaderRegistry(this);
        }
    }
}

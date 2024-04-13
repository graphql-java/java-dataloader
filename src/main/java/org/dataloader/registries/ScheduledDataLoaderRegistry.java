package org.dataloader.registries;

import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.annotations.ExperimentalApi;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.dataloader.impl.Assertions.nonNull;

/**
 * This {@link DataLoaderRegistry} will use {@link DispatchPredicate}s when {@link #dispatchAll()} is called
 * to test (for each {@link DataLoader} in the registry) if a dispatch should proceed.  If the predicate returns false, then a task is scheduled
 * to perform that predicate dispatch again via the {@link ScheduledExecutorService}.
 * <p>
 * It;s possible to have a {@link DispatchPredicate} per dataloader as well as a default {@link DispatchPredicate} for the
 * whole {@link ScheduledDataLoaderRegistry}.
 * <p>
 * This will continue to loop (test false and reschedule) until such time as the predicate returns true, in which case
 * no rescheduling will occur, and you will need to call dispatch again to restart the process.
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

    private final Map<DataLoader<?, ?>, DispatchPredicate> dataLoaderPredicates = new ConcurrentHashMap<>();
    private final DispatchPredicate dispatchPredicate;
    private final ScheduledExecutorService scheduledExecutorService;
    private final boolean defaultExecutorUsed;
    private final Duration schedule;
    private final boolean tickerMode;
    private volatile boolean closed;

    private ScheduledDataLoaderRegistry(Builder builder) {
        super();
        this.dataLoaders.putAll(builder.dataLoaders);
        this.scheduledExecutorService = builder.scheduledExecutorService;
        this.defaultExecutorUsed = builder.defaultExecutorUsed;
        this.schedule = builder.schedule;
        this.tickerMode = builder.tickerMode;
        this.closed = false;
        this.dispatchPredicate = builder.dispatchPredicate;
        this.dataLoaderPredicates.putAll(builder.dataLoaderPredicates);
    }

    /**
     * Once closed this registry will never again reschedule checks
     */
    @Override
    public void close() {
        closed = true;
        if (defaultExecutorUsed) {
            scheduledExecutorService.shutdown();
        }
    }

    /**
     * @return executor being used by this registry
     */
    public ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
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

    /**
     * This will combine all the current data loaders in this registry and all the data loaders from the specified registry
     * and return a new combined registry
     *
     * @param registry the registry to combine into this registry
     *
     * @return a new combined registry
     */
    public ScheduledDataLoaderRegistry combine(DataLoaderRegistry registry) {
        Builder combinedBuilder = ScheduledDataLoaderRegistry.newScheduledRegistry()
                .dispatchPredicate(this.dispatchPredicate);
        combinedBuilder.registerAll(this);
        combinedBuilder.registerAll(registry);
        return combinedBuilder.build();
    }


    /**
     * This will unregister a new dataloader
     *
     * @param key the key of the data loader to unregister
     *
     * @return this registry
     */
    public ScheduledDataLoaderRegistry unregister(String key) {
        DataLoader<?, ?> dataLoader = dataLoaders.remove(key);
        if (dataLoader != null) {
            dataLoaderPredicates.remove(dataLoader);
        }
        return this;
    }

    /**
     * @return a map of data loaders to specific dispatch predicates
     */
    public Map<DataLoader<?, ?>, DispatchPredicate> getDataLoaderPredicates() {
        return new LinkedHashMap<>(dataLoaderPredicates);
    }

    /**
     * There is a default predicate that applies to the whole {@link ScheduledDataLoaderRegistry}
     *
     * @return the default dispatch predicate
     */
    public DispatchPredicate getDispatchPredicate() {
        return dispatchPredicate;
    }

    /**
     * This will register a new dataloader and dispatch predicate associated with that data loader
     *
     * @param key               the key to put the data loader under
     * @param dataLoader        the data loader to register
     * @param dispatchPredicate the dispatch predicate to associate with this data loader
     *
     * @return this registry
     */
    public ScheduledDataLoaderRegistry register(String key, DataLoader<?, ?> dataLoader, DispatchPredicate dispatchPredicate) {
        dataLoaders.put(key, dataLoader);
        dataLoaderPredicates.put(dataLoader, dispatchPredicate);
        return this;
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
     * without testing the predicates
     */
    public void dispatchAllImmediately() {
        dispatchAllWithCountImmediately();
    }

    /**
     * This will immediately dispatch the {@link DataLoader}s in the registry
     * without testing the predicates
     *
     * @return total number of entries that were dispatched from registered {@link org.dataloader.DataLoader}s.
     */
    public int dispatchAllWithCountImmediately() {
        return dataLoaders.values().stream()
                .mapToInt(dataLoader -> dataLoader.dispatchWithCounts().getKeysCount())
                .sum();
    }


    /**
     * This will schedule a task to check the predicate and dispatch if true right now.  It will not do
     * a pre-check of the predicate like {@link #dispatchAll()} would
     */
    public void rescheduleNow() {
        dataLoaders.forEach(this::reschedule);
    }

    /**
     * Returns true if the dataloader has a predicate which returned true, OR the overall
     * registry predicate returned true.
     *
     * @param dataLoaderKey the key in the dataloader map
     * @param dataLoader    the dataloader
     *
     * @return true if it should dispatch
     */
    private boolean shouldDispatch(String dataLoaderKey, DataLoader<?, ?> dataLoader) {
        DispatchPredicate dispatchPredicate = dataLoaderPredicates.get(dataLoader);
        if (dispatchPredicate != null) {
            if (dispatchPredicate.test(dataLoaderKey, dataLoader)) {
                return true;
            }
        }
        return this.dispatchPredicate.test(dataLoaderKey, dataLoader);
    }

    private void reschedule(String key, DataLoader<?, ?> dataLoader) {
        if (!closed) {
            Runnable runThis = () -> dispatchOrReschedule(key, dataLoader);
            scheduledExecutorService.schedule(runThis, schedule.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private int dispatchOrReschedule(String key, DataLoader<?, ?> dataLoader) {
        int sum = 0;
        boolean shouldDispatch = shouldDispatch(key, dataLoader);
        if (shouldDispatch) {
            sum = dataLoader.dispatchWithCounts().getKeysCount();
        }
        if (tickerMode || !shouldDispatch) {
            reschedule(key, dataLoader);
        }
        return sum;
    }

    /**
     * By default, this will create use a {@link Executors#newSingleThreadScheduledExecutor()}
     * and a schedule duration of 10 milliseconds.
     *
     * @return A builder of {@link ScheduledDataLoaderRegistry}s
     */
    public static Builder newScheduledRegistry() {
        return new Builder();
    }

    public static class Builder {

        private final Map<String, DataLoader<?, ?>> dataLoaders = new LinkedHashMap<>();
        private final Map<DataLoader<?, ?>, DispatchPredicate> dataLoaderPredicates = new LinkedHashMap<>();
        private DispatchPredicate dispatchPredicate = DispatchPredicate.DISPATCH_ALWAYS;
        private ScheduledExecutorService scheduledExecutorService;
        private boolean defaultExecutorUsed = false;
        private Duration schedule = Duration.ofMillis(10);
        private boolean tickerMode = false;

        /**
         * If you provide a {@link ScheduledExecutorService} then it will NOT be shutdown when
         * {@link ScheduledDataLoaderRegistry#close()} is called.  This is left to the code that made this setup code
         *
         * @param executorService the executor service to run the ticker on
         *
         * @return this builder for a fluent pattern
         */
        public Builder scheduledExecutorService(ScheduledExecutorService executorService) {
            this.scheduledExecutorService = nonNull(executorService);
            return this;
        }

        public Builder schedule(Duration schedule) {
            this.schedule = schedule;
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
         * This will register a new dataloader with a specific {@link DispatchPredicate}
         *
         * @param key               the key to put the data loader under
         * @param dataLoader        the data loader to register
         * @param dispatchPredicate the dispatch predicate
         *
         * @return this builder for a fluent pattern
         */
        public Builder register(String key, DataLoader<?, ?> dataLoader, DispatchPredicate dispatchPredicate) {
            register(key, dataLoader);
            dataLoaderPredicates.put(dataLoader, dispatchPredicate);
            return this;
        }

        /**
         * This will combine the data loaders in this builder with the ones
         * from a previous {@link DataLoaderRegistry}
         *
         * @param otherRegistry the previous {@link DataLoaderRegistry}
         *
         * @return this builder for a fluent pattern
         */
        public Builder registerAll(DataLoaderRegistry otherRegistry) {
            dataLoaders.putAll(otherRegistry.getDataLoadersMap());
            if (otherRegistry instanceof ScheduledDataLoaderRegistry) {
                ScheduledDataLoaderRegistry other = (ScheduledDataLoaderRegistry) otherRegistry;
                dataLoaderPredicates.putAll(other.dataLoaderPredicates);
            }
            return this;
        }

        /**
         * This sets a default predicate on the {@link DataLoaderRegistry} that will control
         * whether all {@link DataLoader}s in the {@link DataLoaderRegistry }should be dispatched.
         *
         * @param dispatchPredicate the predicate
         *
         * @return this builder for a fluent pattern
         */
        public Builder dispatchPredicate(DispatchPredicate dispatchPredicate) {
            this.dispatchPredicate = dispatchPredicate;
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
            if (scheduledExecutorService == null) {
                scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
                defaultExecutorUsed = true;
            }
            return new ScheduledDataLoaderRegistry(this);
        }
    }
}

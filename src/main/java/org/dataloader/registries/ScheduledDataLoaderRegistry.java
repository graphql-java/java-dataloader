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
 * This {@link DataLoaderRegistry} will use a {@link DispatchPredicate} when {@link #dispatchAll()} is called
 * to test (for each {@link DataLoader} in the registry) if a dispatch should proceed.  If the predicate returns false, then a task is scheduled
 * to perform that predicate dispatch again via the {@link ScheduledExecutorService}.
 * <p>
 * This will continue to loop (test false and reschedule) until such time as the predicate returns true, in which case
 * no rescheduling will occur and you will need to call dispatch again to restart the process.
 * <p>
 * If you wanted to create a ScheduledDataLoaderRegistry that started a rescheduling immediately, just create one and
 * call {@link #rescheduleNow()}.
 * <p>
 * This code is currently marked as {@link ExperimentalApi}
 */
@ExperimentalApi
public class ScheduledDataLoaderRegistry extends DataLoaderRegistry implements AutoCloseable {

    private final Map<DataLoader<?, ?>, DispatchPredicate> dataLoaderPredicates = new ConcurrentHashMap<>();
    private final DispatchPredicate dispatchPredicate;
    private final ScheduledExecutorService scheduledExecutorService;
    private final Duration schedule;
    private volatile boolean closed;

    private ScheduledDataLoaderRegistry(Builder builder) {
        super();
        this.dataLoaders.putAll(builder.dataLoaders);
        this.scheduledExecutorService = builder.scheduledExecutorService;
        this.schedule = builder.schedule;
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
    }

    /**
     * @return how long the {@link ScheduledExecutorService} task will wait before checking the predicate again
     */
    public Duration getScheduleDuration() {
        return schedule;
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
     * @return the current dispatch predicate
     */
    public DispatchPredicate getDispatchPredicate() {
        return dispatchPredicate;
    }

    /**
     * @return a map of data loaders to specific dispatch predicates
     */
    public Map<DataLoader<?, ?>, DispatchPredicate> getDataLoaderPredicates() {
        return new LinkedHashMap<>(dataLoaderPredicates);
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
            if (shouldDispatch(key, dataLoader)) {
                sum += dataLoader.dispatchWithCounts().getKeysCount();
            } else {
                reschedule(key, dataLoader);
            }
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

    private void dispatchOrReschedule(String key, DataLoader<?, ?> dataLoader) {
        if (shouldDispatch(key, dataLoader)) {
            dataLoader.dispatch();
        } else {
            reschedule(key, dataLoader);
        }
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
        private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        private Duration schedule = Duration.ofMillis(10);

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
         * This sets a predicate on the {@link DataLoaderRegistry} that will control
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
         * @return the newly built {@link ScheduledDataLoaderRegistry}
         */
        public ScheduledDataLoaderRegistry build() {
            return new ScheduledDataLoaderRegistry(this);
        }
    }
}

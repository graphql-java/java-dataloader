package org.dataloader.registries;

import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.annotations.ExperimentalApi;

import java.time.Duration;
import java.util.Map;
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

    private final ScheduledExecutorService scheduledExecutorService;
    private final Duration schedule;
    private volatile boolean closed;

    private ScheduledDataLoaderRegistry(Builder builder) {
        super(builder);
        this.scheduledExecutorService = builder.scheduledExecutorService;
        this.schedule = builder.schedule;
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
            if (dispatchPredicate.test(key, dataLoader)) {
                sum += dataLoader.dispatchWithCounts().getKeysCount();
            } else {
                reschedule(key, dataLoader);
            }
        }
        return sum;
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
     * By default this will create use a {@link Executors#newSingleThreadScheduledExecutor()}
     * and a schedule duration of 10 milli seconds.
     *
     * @return A builder of {@link ScheduledDataLoaderRegistry}s
     */
    public static Builder newScheduledRegistry() {
        return new Builder();
    }

    public static class Builder extends DataLoaderRegistry.Builder<ScheduledDataLoaderRegistry.Builder> {

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
         * @return the newly built {@link ScheduledDataLoaderRegistry}
         */
        public ScheduledDataLoaderRegistry build() {
            return new ScheduledDataLoaderRegistry(this);
        }
    }
}

package org.dataloader.strategy;

import org.dataloader.DataLoaderRegistry;
import org.dataloader.DispatchStrategy;
import org.dataloader.impl.Assertions;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BreadthFirstChainedDispatchStrategy implements DispatchStrategy {

    private static final Duration DEFAULT_FALLBACK_TIMEOUT = Duration.ofMillis(30);

    private final ScheduledExecutorService scheduledExecutorService;
    private final AtomicInteger pendingLoadCount = new AtomicInteger(0);
    private final AtomicInteger totalWorkCount = new AtomicInteger(0);
    private final Object dispatchLock = new Object();

    private final Duration fallbackTimeout;
    @Nullable private ScheduledFuture<?> fallbackDispatchFuture = null;

    @Nullable private Runnable dispatchCallback;

    private BreadthFirstChainedDispatchStrategy(Builder builder) {
        this.scheduledExecutorService = builder.scheduledExecutorService;
        this.fallbackTimeout = builder.fallbackTimeout;
    }

    @Override
    public void onRegistryCreation(DataLoaderRegistry registry) {
        dispatchCallback = registry::dispatchAll;
    }

    @Override
    public void loadCalled() {
        // initial load called
        pendingLoadCount.incrementAndGet();
        totalWorkCount.incrementAndGet();
        if (totalWorkCount.get() == 1) {
            triggerDeterministicDispatch();
        }
    }

    @Override
    public void loadCompleted() {
        pendingLoadCount.decrementAndGet();
    }

    private void triggerDeterministicDispatch() {
        synchronized (dispatchLock) {
            if (dispatchCallback == null) {
                throw new IllegalStateException("Dispatch strategy started without being registered to registry");
            }

            // sanity check
            if (pendingLoadCount.get() == 0) {
                return;
            }

            while (pendingLoadCount.get() > 0) {
                int workBefore = totalWorkCount.get();

                dispatchCallback.run();

                int workAfter = totalWorkCount.get();
                int pendingAfter = pendingLoadCount.get();

                // no progress but not done - trigger async check
                if (workAfter == workBefore && pendingAfter > 0) {
                    scheduleFallbackDispatch();
                    break;
                }

                // completed
                if (pendingAfter == 0) {
                    resetState();
                }
            }
        }
    }

    private synchronized void scheduleFallbackDispatch() {
        // fallback already scheduled, don't reschedule
        if (fallbackDispatchFuture != null && !fallbackDispatchFuture.isDone()) {
            return;
        }

        fallbackDispatchFuture =
                scheduledExecutorService.schedule(
                        () -> {
                            // clear the future so we can start scheduling again
                            synchronized (this) {
                                fallbackDispatchFuture = null;
                            }
                            triggerDeterministicDispatch();
                        },
                        fallbackTimeout.toMillis(),
                        TimeUnit.MILLISECONDS
                );
    }

    private void resetState() {
        pendingLoadCount.set(0);
        totalWorkCount.set(0);
        if (fallbackDispatchFuture != null) {
            fallbackDispatchFuture.cancel(false);
        }
    }

    public static class Builder {
        private Duration fallbackTimeout = DEFAULT_FALLBACK_TIMEOUT;
        private final ScheduledExecutorService scheduledExecutorService;

        public Builder(ScheduledExecutorService scheduledExecutorService) {
            this.scheduledExecutorService = Assertions.nonNull(scheduledExecutorService);
        }

        public Builder setFallbackTimeout(Duration fallbackTimeout) {
            this.fallbackTimeout = Assertions.nonNull(fallbackTimeout);
            return this;
        }

        public BreadthFirstChainedDispatchStrategy build() {
            return new BreadthFirstChainedDispatchStrategy(this);
        }

    }
}

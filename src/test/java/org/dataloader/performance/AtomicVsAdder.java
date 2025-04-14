package org.dataloader.performance;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class AtomicVsAdder {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    public static void main(final String[] args) throws Exception {
        // knobs
        final var iterationsList = List.of(1 << 20L, 1 << 24L);
        final var numberOfThreadsList = List.of(1, 2, 4, 8, 16);
        final var strategies = List.of(new LongAdderStrategy(), new AtomicLongStrategy());

        // test
        System.out.println("testing with #cpu=" + Runtime.getRuntime().availableProcessors());
        for (int iterations : iterationsList) {
            for (int numberOfThreads : numberOfThreadsList) {
                for (Strategy strategy : strategies) {
                    performTest(iterations, numberOfThreads, strategy);
                }
            }
        }

        EXECUTOR.shutdownNow();

    }

    private static void performTest(final long iterations, final int numberOfThreads, Strategy strategy) throws Exception {
        final List<Future<?>> futures = new ArrayList<>();
        System.out.println("start test with " + iterations + " iterations using "  + numberOfThreads + " threads and strategy " + strategy.getClass().getSimpleName());
        final long start = System.nanoTime();

        for (int i = 0; i < numberOfThreads; i++) {
            Future<?> submit = EXECUTOR.submit(() -> concurrentWork(strategy, iterations));
            futures.add(submit);
        }
        for (final Future<?> future : futures) {
            future.get(); // wait for all
        }
        final long end = System.nanoTime();
        System.out.println("done in " + Duration.ofNanos(end - start).toMillis() + "ms => result " + strategy.get());
        System.out.println("----");
        strategy.reset();
    }

    @SuppressWarnings("SameParameterValue")
    private static void concurrentWork(final Strategy strategy, final long iterations) {
        long work = iterations;
        while (work-- > 0) {
            strategy.increment();
        }
    }

    interface Strategy {
        void increment();

        long get();

        void reset();
    }

    static class LongAdderStrategy implements Strategy {

        private LongAdder longAdder = new LongAdder();

        @Override
        public void increment() {
            longAdder.increment();
        }

        @Override
        public long get() {
            return longAdder.sum();
        }

        @Override
        public void reset() {
            longAdder = new LongAdder();
        }
    }

    static class AtomicLongStrategy implements Strategy {

        private final AtomicLong atomicLong = new AtomicLong(0);

        @Override
        public void increment() {
            atomicLong.incrementAndGet();
        }

        @Override
        public long get() {
            return atomicLong.get();
        }

        @Override
        public void reset() {
            atomicLong.set(0);
        }
    }

}
package performance;

import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;
import org.dataloader.registries.DispatchPredicate;
import org.dataloader.registries.ScheduledDataLoaderRegistry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A ticker-mode {@link ScheduledDataLoaderRegistry} set up the way a GraphQL server builds one per request: every
 * data loader the application defines is registered, but a request only queues keys on a few of them, while
 * graphql-java's per-level dispatch calls {@link ScheduledDataLoaderRegistry#dispatchAll()} many times.
 * <p>
 * The tick is an hour so that the scheduled ticker never runs during a measurement.
 */
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ScheduledDataLoaderRegistryDispatchPerformance {

    @Param({"20", "200"})
    int registeredLoaders;

    @Param({"0", "5"})
    int loadersWithQueuedKeys;

    ScheduledThreadPoolExecutor executor;
    ScheduledDataLoaderRegistry registry;
    List<DataLoader<Integer, Integer>> queuedLoaders;

    @Setup
    public void setup() {
        executor = new ScheduledThreadPoolExecutor(1);
        ScheduledDataLoaderRegistry.Builder builder = ScheduledDataLoaderRegistry.newScheduledRegistry()
                .scheduledExecutorService(executor)
                .tickerMode(true)
                .schedule(Duration.ofHours(1))
                .dispatchPredicate(DispatchPredicate.DISPATCH_NEVER);
        BatchLoader<Integer, Integer> echo = CompletableFuture::completedFuture;
        DataLoaderOptions uncached = DataLoaderOptions.newOptions().setCachingEnabled(false).build();
        List<DataLoader<Integer, Integer>> loaders = new ArrayList<>();
        for (int i = 0; i < registeredLoaders; i++) {
            DataLoader<Integer, Integer> loader = DataLoaderFactory.newDataLoader("loader-" + i, echo, uncached);
            loaders.add(loader);
            builder.register("loader-" + i, loader, DispatchPredicate.DISPATCH_ALWAYS);
        }
        registry = builder.build();
        // spread across the registry rather than the first few entries
        queuedLoaders = new ArrayList<>();
        for (int i = 0; i < loadersWithQueuedKeys; i++) {
            queuedLoaders.add(loaders.get(i * registeredLoaders / loadersWithQueuedKeys));
        }
    }

    @TearDown
    public void tearDown() {
        registry.close();
        executor.shutdownNow();
    }

    @Benchmark
    public int dispatchAll() {
        for (DataLoader<Integer, Integer> loader : queuedLoaders) {
            loader.load(1);
        }
        return registry.dispatchAllWithCount();
    }
}

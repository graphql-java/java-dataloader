package org.dataloader;

import org.dataloader.impl.CompletableFutureKit;
import org.dataloader.stats.SimpleStatisticsCollector;
import org.dataloader.stats.Statistics;
import org.dataloader.stats.StatisticsCollector;
import org.dataloader.stats.context.IncrementBatchLoadCountByStatisticsContext;
import org.dataloader.stats.context.IncrementBatchLoadExceptionCountStatisticsContext;
import org.dataloader.stats.context.IncrementCacheHitCountStatisticsContext;
import org.dataloader.stats.context.IncrementLoadCountStatisticsContext;
import org.dataloader.stats.context.IncrementLoadErrorCountStatisticsContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.dataloader.DataLoaderFactory.newDataLoader;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

/**
 * Tests related to stats.  DataLoaderTest is getting to big and needs refactoring
 */
public class DataLoaderStatsTest {

    @Test
    public void stats_are_collected_by_default() {
        BatchLoader<String, String> batchLoader = CompletableFuture::completedFuture;
        DataLoader<String, String> loader = newDataLoader(batchLoader,
                DataLoaderOptions.newOptions().setStatisticsCollector(SimpleStatisticsCollector::new).build()
        );

        loader.load("A");
        loader.load("B");
        loader.loadMany(asList("C", "D"));

        Statistics stats = loader.getStatistics();
        assertThat(stats.getLoadCount(), equalTo(4L));
        assertThat(stats.getBatchInvokeCount(), equalTo(0L));
        assertThat(stats.getBatchLoadCount(), equalTo(0L));
        assertThat(stats.getCacheHitCount(), equalTo(0L));

        loader.dispatch();

        stats = loader.getStatistics();
        assertThat(stats.getLoadCount(), equalTo(4L));
        assertThat(stats.getBatchInvokeCount(), equalTo(1L));
        assertThat(stats.getBatchLoadCount(), equalTo(4L));
        assertThat(stats.getCacheHitCount(), equalTo(0L));

        loader.load("A");
        loader.load("B");

        loader.dispatch();

        stats = loader.getStatistics();
        assertThat(stats.getLoadCount(), equalTo(6L));
        assertThat(stats.getBatchInvokeCount(), equalTo(1L));
        assertThat(stats.getBatchLoadCount(), equalTo(4L));
        assertThat(stats.getCacheHitCount(), equalTo(2L));
    }


    @Test
    public void stats_are_collected_with_specified_collector() {
        // let's prime it with some numbers, so we know it's ours
        StatisticsCollector collector = new SimpleStatisticsCollector();
        collector.incrementLoadCount(new IncrementLoadCountStatisticsContext<>(1, null));
        collector.incrementBatchLoadCountBy(1, new IncrementBatchLoadCountByStatisticsContext<>(1, null));

        BatchLoader<String, String> batchLoader = CompletableFuture::completedFuture;
        DataLoaderOptions loaderOptions = DataLoaderOptions.newOptions().setStatisticsCollector(() -> collector).build();
        DataLoader<String, String> loader = newDataLoader(batchLoader, loaderOptions);

        loader.load("A");
        loader.load("B");
        loader.loadMany(asList("C", "D"));

        Statistics stats = loader.getStatistics();
        assertThat(stats.getLoadCount(), equalTo(5L)); // previously primed with 1
        assertThat(stats.getBatchInvokeCount(), equalTo(1L)); // also primed
        assertThat(stats.getBatchLoadCount(), equalTo(1L));  // also primed
        assertThat(stats.getCacheHitCount(), equalTo(0L));

        loader.dispatch();

        stats = loader.getStatistics();
        assertThat(stats.getLoadCount(), equalTo(5L));
        assertThat(stats.getBatchInvokeCount(), equalTo(2L));
        assertThat(stats.getBatchLoadCount(), equalTo(5L));
        assertThat(stats.getCacheHitCount(), equalTo(0L));

        loader.load("A");
        loader.load("B");

        loader.dispatch();

        stats = loader.getStatistics();
        assertThat(stats.getLoadCount(), equalTo(7L));
        assertThat(stats.getBatchInvokeCount(), equalTo(2L));
        assertThat(stats.getBatchLoadCount(), equalTo(5L));
        assertThat(stats.getCacheHitCount(), equalTo(2L));
    }

    @Test
    public void stats_are_collected_with_caching_disabled() {
        StatisticsCollector collector = new SimpleStatisticsCollector();

        BatchLoader<String, String> batchLoader = CompletableFuture::completedFuture;
        DataLoaderOptions loaderOptions = DataLoaderOptions.newOptions().setStatisticsCollector(() -> collector).setCachingEnabled(false).build();
        DataLoader<String, String> loader = newDataLoader(batchLoader, loaderOptions);

        loader.load("A");
        loader.load("B");
        loader.loadMany(asList("C", "D"));
        loader.loadMany(Map.of("E", "E", "F", "F"));

        Statistics stats = loader.getStatistics();
        assertThat(stats.getLoadCount(), equalTo(6L));
        assertThat(stats.getBatchInvokeCount(), equalTo(0L));
        assertThat(stats.getBatchLoadCount(), equalTo(0L));
        assertThat(stats.getCacheHitCount(), equalTo(0L));

        loader.dispatch();

        stats = loader.getStatistics();
        assertThat(stats.getLoadCount(), equalTo(6L));
        assertThat(stats.getBatchInvokeCount(), equalTo(1L));
        assertThat(stats.getBatchLoadCount(), equalTo(6L));
        assertThat(stats.getCacheHitCount(), equalTo(0L));

        loader.load("A");
        loader.load("B");

        loader.dispatch();

        stats = loader.getStatistics();
        assertThat(stats.getLoadCount(), equalTo(8L));
        assertThat(stats.getBatchInvokeCount(), equalTo(2L));
        assertThat(stats.getBatchLoadCount(), equalTo(8L));
        assertThat(stats.getCacheHitCount(), equalTo(0L));
    }

    BatchLoader<String, Try<String>> batchLoaderThatBlows = keys -> {
        List<Try<String>> values = new ArrayList<>();
        for (String key : keys) {
            if (key.startsWith("exception")) {
                return CompletableFutureKit.failedFuture(new RuntimeException(key));
            } else if (key.startsWith("bang")) {
                throw new RuntimeException(key);
            } else if (key.startsWith("error")) {
                values.add(Try.failed(new RuntimeException(key)));
            } else {
                values.add(Try.succeeded(key));
            }
        }
        return completedFuture(values);
    };

    @Test
    public void stats_are_collected_on_exceptions() {
        DataLoader<String, String> loader = DataLoaderFactory.newDataLoaderWithTry(batchLoaderThatBlows,
                DataLoaderOptions.newOptions().setStatisticsCollector(SimpleStatisticsCollector::new).build()
        );

        loader.load("A");
        loader.load("exception");

        Statistics stats = loader.getStatistics();
        assertThat(stats.getLoadCount(), equalTo(2L));
        assertThat(stats.getBatchLoadExceptionCount(), equalTo(0L));
        assertThat(stats.getLoadErrorCount(), equalTo(0L));

        loader.dispatch();

        stats = loader.getStatistics();
        assertThat(stats.getLoadCount(), equalTo(2L));
        assertThat(stats.getBatchInvokeCount(), equalTo(1L));
        assertThat(stats.getBatchLoadCount(), equalTo(2L));
        assertThat(stats.getBatchLoadExceptionCount(), equalTo(1L));
        assertThat(stats.getLoadErrorCount(), equalTo(0L));

        loader.load("error1");
        loader.load("error2");
        loader.load("error3");

        loader.dispatch();

        stats = loader.getStatistics();
        assertThat(stats.getLoadCount(), equalTo(5L));
        assertThat(stats.getBatchLoadCount(), equalTo(5L));
        assertThat(stats.getBatchLoadExceptionCount(), equalTo(1L));
        assertThat(stats.getLoadErrorCount(), equalTo(3L));

        loader.load("bang");

        loader.dispatch();

        stats = loader.getStatistics();
        assertThat(stats.getLoadCount(), equalTo(6L));
        assertThat(stats.getBatchLoadCount(), equalTo(6L));
        assertThat(stats.getBatchLoadExceptionCount(), equalTo(2L));
        assertThat(stats.getLoadErrorCount(), equalTo(3L));
    }

    /**
     * A simple {@link StatisticsCollector} that stores the contexts passed to it.
     */
    private static class ContextPassingStatisticsCollector implements StatisticsCollector {

        public List<IncrementLoadCountStatisticsContext<?>> incrementLoadCountStatisticsContexts = new ArrayList<>();
        public List<IncrementLoadErrorCountStatisticsContext<?>> incrementLoadErrorCountStatisticsContexts = new ArrayList<>();
        public List<IncrementBatchLoadCountByStatisticsContext<?>> incrementBatchLoadCountByStatisticsContexts = new ArrayList<>();
        public List<IncrementBatchLoadExceptionCountStatisticsContext<?>> incrementBatchLoadExceptionCountStatisticsContexts = new ArrayList<>();
        public List<IncrementCacheHitCountStatisticsContext<?>> incrementCacheHitCountStatisticsContexts = new ArrayList<>();

        @Override
        public <K> void incrementLoadCount(IncrementLoadCountStatisticsContext<K> context) {
            incrementLoadCountStatisticsContexts.add(context);
        }

        @Deprecated
        @Override
        public void incrementLoadCount() {

        }

        @Override
        public <K> void incrementLoadErrorCount(IncrementLoadErrorCountStatisticsContext<K> context) {
            incrementLoadErrorCountStatisticsContexts.add(context);
        }

        @Deprecated
        @Override
        public void incrementLoadErrorCount() {

        }

        @Override
        public <K> void incrementBatchLoadCountBy(long delta, IncrementBatchLoadCountByStatisticsContext<K> context) {
            incrementBatchLoadCountByStatisticsContexts.add(context);
        }

        @Deprecated
        @Override
        public void incrementBatchLoadCountBy(long delta) {
        }

        @Override
        public <K> void incrementBatchLoadExceptionCount(IncrementBatchLoadExceptionCountStatisticsContext<K> context) {
            incrementBatchLoadExceptionCountStatisticsContexts.add(context);
        }

        @Deprecated
        @Override
        public void incrementBatchLoadExceptionCount() {
        }

        @Override
        public <K> void incrementCacheHitCount(IncrementCacheHitCountStatisticsContext<K> context) {
            incrementCacheHitCountStatisticsContexts.add(context);
        }

        @Deprecated
        @Override
        public void incrementCacheHitCount() {
        }

        @Override
        public Statistics getStatistics() {
            return null;
        }
    }

    @Test
    public void context_is_passed_through_to_collector() {
        ContextPassingStatisticsCollector statisticsCollector = new ContextPassingStatisticsCollector();
        DataLoader<String, Try<String>> loader = newDataLoader(batchLoaderThatBlows,
                DataLoaderOptions.newOptions().setStatisticsCollector(() -> statisticsCollector).build()
        );

        loader.load("key", "keyContext");
        assertThat(statisticsCollector.incrementLoadCountStatisticsContexts, hasSize(1));
        assertThat(statisticsCollector.incrementLoadCountStatisticsContexts.get(0).getKey(), equalTo("key"));
        assertThat(statisticsCollector.incrementLoadCountStatisticsContexts.get(0).getCallContext(), equalTo("keyContext"));

        loader.load("key", "keyContext");
        assertThat(statisticsCollector.incrementCacheHitCountStatisticsContexts, hasSize(1));
        assertThat(statisticsCollector.incrementCacheHitCountStatisticsContexts.get(0).getKey(), equalTo("key"));
        assertThat(statisticsCollector.incrementCacheHitCountStatisticsContexts.get(0).getCallContext(), equalTo("keyContext"));

        loader.dispatch();
        assertThat(statisticsCollector.incrementBatchLoadCountByStatisticsContexts, hasSize(1));
        assertThat(statisticsCollector.incrementBatchLoadCountByStatisticsContexts.get(0).getKeys(), equalTo(singletonList("key")));
        assertThat(statisticsCollector.incrementBatchLoadCountByStatisticsContexts.get(0).getCallContexts(), equalTo(singletonList("keyContext")));

        loader.load("exception", "exceptionKeyContext");
        loader.dispatch();
        assertThat(statisticsCollector.incrementBatchLoadExceptionCountStatisticsContexts, hasSize(1));
        assertThat(statisticsCollector.incrementBatchLoadExceptionCountStatisticsContexts.get(0).getKeys(), equalTo(singletonList("exception")));
        assertThat(statisticsCollector.incrementBatchLoadExceptionCountStatisticsContexts.get(0).getCallContexts(), equalTo(singletonList("exceptionKeyContext")));

        loader.load("error", "errorKeyContext");
        loader.dispatch();
        assertThat(statisticsCollector.incrementLoadErrorCountStatisticsContexts, hasSize(1));
        assertThat(statisticsCollector.incrementLoadErrorCountStatisticsContexts.get(0).getKey(), equalTo("error"));
        assertThat(statisticsCollector.incrementLoadErrorCountStatisticsContexts.get(0).getCallContext(), equalTo("errorKeyContext"));
    }
}

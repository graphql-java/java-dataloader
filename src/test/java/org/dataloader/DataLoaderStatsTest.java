package org.dataloader;

import org.dataloader.impl.CompletableFutureKit;
import org.dataloader.stats.SimpleStatisticsCollector;
import org.dataloader.stats.Statistics;
import org.dataloader.stats.StatisticsCollector;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.Arrays.asList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.dataloader.DataLoaderFactory.newDataLoader;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * Tests related to stats.  DataLoaderTest is getting to big and needs refactoring
 */
public class DataLoaderStatsTest {

    @Test
    public void stats_are_collected_by_default() {
        BatchLoader<String, String> batchLoader = CompletableFuture::completedFuture;
        DataLoader<String, String> loader = newDataLoader(batchLoader);

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
        // lets prime it with some numbers so we know its ours
        StatisticsCollector collector = new SimpleStatisticsCollector();
        collector.incrementLoadCount();
        collector.incrementBatchLoadCountBy(1);

        BatchLoader<String, String> batchLoader = CompletableFuture::completedFuture;
        DataLoaderOptions loaderOptions = DataLoaderOptions.newOptions().setStatisticsCollector(() -> collector);
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
        DataLoaderOptions loaderOptions = DataLoaderOptions.newOptions().setStatisticsCollector(() -> collector).setCachingEnabled(false);
        DataLoader<String, String> loader = newDataLoader(batchLoader, loaderOptions);

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
        assertThat(stats.getBatchInvokeCount(), equalTo(2L));
        assertThat(stats.getBatchLoadCount(), equalTo(6L));
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
        DataLoader<String, String> loader = DataLoaderFactory.newDataLoaderWithTry(batchLoaderThatBlows);

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
}

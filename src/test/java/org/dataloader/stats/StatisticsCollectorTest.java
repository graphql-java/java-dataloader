package org.dataloader.stats;

import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class StatisticsCollectorTest {

    @Test
    public void basic_collection() throws Exception {
        StatisticsCollector collector = new SimpleStatisticsCollector();

        assertThat(collector.getStatistics().getLoadCount(), equalTo(0L));
        assertThat(collector.getStatistics().getBatchLoadCount(), equalTo(0L));
        assertThat(collector.getStatistics().getCacheHitCount(), equalTo(0L));
        assertThat(collector.getStatistics().getCacheMissCount(), equalTo(0L));


        collector.incrementLoadCount();
        collector.incrementBatchLoadCount();
        collector.incrementCacheHitCount();

        assertThat(collector.getStatistics().getLoadCount(), equalTo(1L));
        assertThat(collector.getStatistics().getBatchLoadCount(), equalTo(1L));
        assertThat(collector.getStatistics().getCacheHitCount(), equalTo(1L));
        assertThat(collector.getStatistics().getCacheMissCount(), equalTo(0L));
    }

    @Test
    public void ratios_work() throws Exception {

        StatisticsCollector collector = new SimpleStatisticsCollector();

        collector.incrementLoadCount();

        Statistics stats = collector.getStatistics();
        assertThat(stats.getBatchLoadRatio(), equalTo(0f));
        assertThat(stats.getCacheHitRatio(), equalTo(0f));


        collector.incrementLoadCount();
        collector.incrementLoadCount();
        collector.incrementLoadCount();
        collector.incrementBatchLoadCount();

        stats = collector.getStatistics();
        assertThat(stats.getBatchLoadRatio(), equalTo(1f / 4f));


        collector.incrementLoadCount();
        collector.incrementLoadCount();
        collector.incrementLoadCount();
        collector.incrementCacheHitCount();
        collector.incrementCacheHitCount();

        stats = collector.getStatistics();
        assertThat(stats.getCacheHitRatio(), equalTo(2f / 7f));
    }

    @Test
    public void thread_local_collection() throws Exception {

        final ThreadLocalStatisticsCollector collector = new ThreadLocalStatisticsCollector();

        assertThat(collector.getStatistics().getLoadCount(), equalTo(0L));
        assertThat(collector.getStatistics().getBatchLoadCount(), equalTo(0L));
        assertThat(collector.getStatistics().getCacheHitCount(), equalTo(0L));


        collector.incrementLoadCount();
        collector.incrementBatchLoadCount();
        collector.incrementCacheHitCount();

        assertThat(collector.getStatistics().getLoadCount(), equalTo(1L));
        assertThat(collector.getStatistics().getBatchLoadCount(), equalTo(1L));
        assertThat(collector.getStatistics().getCacheHitCount(), equalTo(1L));

        assertThat(collector.getOverallStatistics().getLoadCount(), equalTo(1L));
        assertThat(collector.getOverallStatistics().getBatchLoadCount(), equalTo(1L));
        assertThat(collector.getOverallStatistics().getCacheHitCount(), equalTo(1L));

        CompletableFuture.supplyAsync(() -> {

            collector.incrementLoadCount();
            collector.incrementBatchLoadCount();
            collector.incrementCacheHitCount();

            // per thread stats here
            assertThat(collector.getStatistics().getLoadCount(), equalTo(1L));
            assertThat(collector.getStatistics().getBatchLoadCount(), equalTo(1L));
            assertThat(collector.getStatistics().getCacheHitCount(), equalTo(1L));

            // overall stats
            assertThat(collector.getOverallStatistics().getLoadCount(), equalTo(2L));
            assertThat(collector.getOverallStatistics().getBatchLoadCount(), equalTo(2L));
            assertThat(collector.getOverallStatistics().getCacheHitCount(), equalTo(2L));

            return null;
        }).join();

        // back on this main thread

        collector.incrementLoadCount();
        collector.incrementBatchLoadCount();
        collector.incrementCacheHitCount();

        // per thread stats here
        assertThat(collector.getStatistics().getLoadCount(), equalTo(2L));
        assertThat(collector.getStatistics().getBatchLoadCount(), equalTo(2L));
        assertThat(collector.getStatistics().getCacheHitCount(), equalTo(2L));

        // overall stats
        assertThat(collector.getOverallStatistics().getLoadCount(), equalTo(3L));
        assertThat(collector.getOverallStatistics().getBatchLoadCount(), equalTo(3L));
        assertThat(collector.getOverallStatistics().getCacheHitCount(), equalTo(3L));


        // stats can be reset per thread
        collector.resetThread();

        // thread is reset
        assertThat(collector.getStatistics().getLoadCount(), equalTo(0L));
        assertThat(collector.getStatistics().getBatchLoadCount(), equalTo(0L));
        assertThat(collector.getStatistics().getCacheHitCount(), equalTo(0L));

        // but not overall stats
        assertThat(collector.getOverallStatistics().getLoadCount(), equalTo(3L));
        assertThat(collector.getOverallStatistics().getBatchLoadCount(), equalTo(3L));
        assertThat(collector.getOverallStatistics().getCacheHitCount(), equalTo(3L));
    }
}
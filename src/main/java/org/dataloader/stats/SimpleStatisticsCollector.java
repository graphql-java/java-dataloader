package org.dataloader.stats;

import org.dataloader.stats.context.IncrementBatchLoadCountByStatisticsContext;
import org.dataloader.stats.context.IncrementBatchLoadExceptionCountStatisticsContext;
import org.dataloader.stats.context.IncrementCacheHitCountStatisticsContext;
import org.dataloader.stats.context.IncrementLoadCountStatisticsContext;
import org.dataloader.stats.context.IncrementLoadErrorCountStatisticsContext;

import java.util.concurrent.atomic.LongAdder;

/**
 * This simple collector uses {@link java.util.concurrent.atomic.AtomicLong}s to collect
 * statistics
 *
 * @see org.dataloader.stats.StatisticsCollector
 */
public class SimpleStatisticsCollector implements StatisticsCollector {

    private final LongAdder loadCount = new LongAdder();
    private final LongAdder batchInvokeCount = new LongAdder();
    private final LongAdder batchLoadCount = new LongAdder();
    private final LongAdder cacheHitCount = new LongAdder();
    private final LongAdder batchLoadExceptionCount = new LongAdder();
    private final LongAdder loadErrorCount = new LongAdder();

    @Override
    public <K> void incrementLoadCount(IncrementLoadCountStatisticsContext<K> context) {
        loadCount.increment();
    }

    @Deprecated
    @Override
    public void incrementLoadCount() {
        incrementLoadCount(null);
    }

    @Override
    public <K> void incrementLoadErrorCount(IncrementLoadErrorCountStatisticsContext<K> context) {
        loadErrorCount.increment();
    }

    @Deprecated
    @Override
    public void incrementLoadErrorCount() {
        incrementLoadErrorCount(null);
    }

    @Override
    public <K> void incrementBatchLoadCountBy(long delta, IncrementBatchLoadCountByStatisticsContext<K> context) {
        batchInvokeCount.increment();
        batchLoadCount.add(delta);
    }

    @Deprecated
    @Override
    public void incrementBatchLoadCountBy(long delta) {
        incrementBatchLoadCountBy(delta, null);
    }

    @Override
    public <K> void incrementBatchLoadExceptionCount(IncrementBatchLoadExceptionCountStatisticsContext<K> context) {
        batchLoadExceptionCount.increment();
    }

    @Deprecated
    @Override
    public void incrementBatchLoadExceptionCount() {
         incrementBatchLoadExceptionCount(null);
    }

    @Override
    public <K> void incrementCacheHitCount(IncrementCacheHitCountStatisticsContext<K> context) {
        cacheHitCount.increment();
    }

    @Deprecated
    @Override
    public void incrementCacheHitCount() {
        incrementCacheHitCount(null);
    }

    @Override
    public Statistics getStatistics() {
        return new Statistics(loadCount.sum(), loadErrorCount.sum(), batchInvokeCount.sum(), batchLoadCount.sum(), batchLoadExceptionCount.sum(), cacheHitCount.sum());
    }

    @Override
    public String toString() {
        return getStatistics().toString();
    }
}

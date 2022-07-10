package org.dataloader.stats;

import org.dataloader.stats.context.IncrementBatchLoadCountByStatisticsContext;
import org.dataloader.stats.context.IncrementBatchLoadExceptionCountStatisticsContext;
import org.dataloader.stats.context.IncrementCacheHitCountStatisticsContext;
import org.dataloader.stats.context.IncrementLoadCountStatisticsContext;
import org.dataloader.stats.context.IncrementLoadErrorCountStatisticsContext;

import java.util.concurrent.atomic.AtomicLong;

/**
 * This simple collector uses {@link java.util.concurrent.atomic.AtomicLong}s to collect
 * statistics
 *
 * @see org.dataloader.stats.StatisticsCollector
 */
public class SimpleStatisticsCollector implements StatisticsCollector {
    private final AtomicLong loadCount = new AtomicLong();
    private final AtomicLong batchInvokeCount = new AtomicLong();
    private final AtomicLong batchLoadCount = new AtomicLong();
    private final AtomicLong cacheHitCount = new AtomicLong();
    private final AtomicLong batchLoadExceptionCount = new AtomicLong();
    private final AtomicLong loadErrorCount = new AtomicLong();

    @Override
    public <K> long incrementLoadCount(IncrementLoadCountStatisticsContext<K> context) {
        return loadCount.incrementAndGet();
    }

    @Deprecated
    @Override
    public long incrementLoadCount() {
        return incrementLoadCount(null);
    }

    @Override
    public <K> long incrementLoadErrorCount(IncrementLoadErrorCountStatisticsContext<K> context) {
        return loadErrorCount.incrementAndGet();
    }

    @Deprecated
    @Override
    public long incrementLoadErrorCount() {
        return incrementLoadErrorCount(null);
    }

    @Override
    public <K> long incrementBatchLoadCountBy(long delta, IncrementBatchLoadCountByStatisticsContext<K> context) {
        batchInvokeCount.incrementAndGet();
        return batchLoadCount.addAndGet(delta);
    }

    @Deprecated
    @Override
    public long incrementBatchLoadCountBy(long delta) {
        return incrementBatchLoadCountBy(delta, null);
    }

    @Override
    public <K> long incrementBatchLoadExceptionCount(IncrementBatchLoadExceptionCountStatisticsContext<K> context) {
        return batchLoadExceptionCount.incrementAndGet();
    }

    @Deprecated
    @Override
    public long incrementBatchLoadExceptionCount() {
        return incrementBatchLoadExceptionCount(null);
    }

    @Override
    public <K> long incrementCacheHitCount(IncrementCacheHitCountStatisticsContext<K> context) {
        return cacheHitCount.incrementAndGet();
    }

    @Deprecated
    @Override
    public long incrementCacheHitCount() {
        return incrementCacheHitCount(null);
    }

    @Override
    public Statistics getStatistics() {
        return new Statistics(loadCount.get(), loadErrorCount.get(), batchInvokeCount.get(), batchLoadCount.get(), batchLoadExceptionCount.get(), cacheHitCount.get());
    }

    @Override
    public String toString() {
        return getStatistics().toString();
    }
}

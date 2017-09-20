package org.dataloader.stats;

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
    public long incrementLoadCount() {
        return loadCount.incrementAndGet();
    }


    @Override
    public long incrementBatchLoadCountBy(long delta) {
        batchInvokeCount.incrementAndGet();
        return batchLoadCount.addAndGet(delta);
    }

    @Override
    public long incrementCacheHitCount() {
        return cacheHitCount.incrementAndGet();
    }

    @Override
    public long incrementLoadErrorCount() {
        return loadErrorCount.incrementAndGet();
    }

    @Override
    public long incrementBatchLoadExceptionCount() {
        return batchLoadExceptionCount.incrementAndGet();
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

package org.dataloader.stats;

import org.dataloader.stats.context.IncrementBatchLoadCountByStatisticsContext;
import org.dataloader.stats.context.IncrementBatchLoadExceptionCountStatisticsContext;
import org.dataloader.stats.context.IncrementCacheHitCountStatisticsContext;
import org.dataloader.stats.context.IncrementLoadCountStatisticsContext;
import org.dataloader.stats.context.IncrementLoadErrorCountStatisticsContext;

/**
 * A statistics collector that does nothing
 */
public class NoOpStatisticsCollector implements StatisticsCollector {

    private static final Statistics ZERO_STATS = new Statistics();

    @Override
    public <K> long incrementLoadCount(IncrementLoadCountStatisticsContext<K> context) {
        return 0;
    }

    @Deprecated
    @Override
    public long incrementLoadCount() {
        return incrementLoadCount(null);
    }

    @Override
    public <K> long incrementLoadErrorCount(IncrementLoadErrorCountStatisticsContext<K> context) {
        return 0;
    }

    @Deprecated
    @Override
    public long incrementLoadErrorCount() {
        return incrementLoadErrorCount(null);
    }

    @Override
    public <K> long incrementBatchLoadCountBy(long delta, IncrementBatchLoadCountByStatisticsContext<K> context) {
        return 0;
    }

    @Deprecated
    @Override
    public long incrementBatchLoadCountBy(long delta) {
        return incrementBatchLoadCountBy(delta, null);
    }

    @Override
    public <K> long incrementBatchLoadExceptionCount(IncrementBatchLoadExceptionCountStatisticsContext<K> context) {
        return 0;
    }

    @Deprecated
    @Override
    public long incrementBatchLoadExceptionCount() {
        return incrementBatchLoadExceptionCount(null);
    }

    @Override
    public <K> long incrementCacheHitCount(IncrementCacheHitCountStatisticsContext<K> context) {
        return 0;
    }

    @Deprecated
    @Override
    public long incrementCacheHitCount() {
        return incrementCacheHitCount(null);
    }

    @Override
    public Statistics getStatistics() {
        return ZERO_STATS;
    }
}

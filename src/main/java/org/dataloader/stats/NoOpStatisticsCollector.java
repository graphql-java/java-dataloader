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
    public <K> void incrementLoadCount(IncrementLoadCountStatisticsContext<K> context) {
    }

    @Deprecated
    @Override
    public void incrementLoadCount() {
        incrementLoadCount(null);
    }

    @Override
    public <K> void incrementLoadErrorCount(IncrementLoadErrorCountStatisticsContext<K> context) {
    }

    @Deprecated
    @Override
    public void incrementLoadErrorCount() {
        incrementLoadErrorCount(null);
    }

    @Override
    public <K> void incrementBatchLoadCountBy(long delta, IncrementBatchLoadCountByStatisticsContext<K> context) {
    }

    @Deprecated
    @Override
    public void incrementBatchLoadCountBy(long delta) {
         incrementBatchLoadCountBy(delta, null);
    }

    @Override
    public <K> void incrementBatchLoadExceptionCount(IncrementBatchLoadExceptionCountStatisticsContext<K> context) {

    }

    @Deprecated
    @Override
    public void incrementBatchLoadExceptionCount() {
        incrementBatchLoadExceptionCount(null);
    }

    @Override
    public <K> void incrementCacheHitCount(IncrementCacheHitCountStatisticsContext<K> context) {
    }

    @Deprecated
    @Override
    public void incrementCacheHitCount() {
        incrementCacheHitCount(null);
    }

    @Override
    public Statistics getStatistics() {
        return ZERO_STATS;
    }
}

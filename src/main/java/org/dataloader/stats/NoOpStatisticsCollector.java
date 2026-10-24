package org.dataloader.stats;

import org.dataloader.stats.context.IncrementBatchLoadCountByStatisticsContext;
import org.dataloader.stats.context.IncrementBatchLoadExceptionCountStatisticsContext;
import org.dataloader.stats.context.IncrementCacheHitCountStatisticsContext;
import org.dataloader.stats.context.IncrementLoadCountStatisticsContext;
import org.dataloader.stats.context.IncrementLoadErrorCountStatisticsContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A statistics collector that does nothing
 */
@NullMarked
public class NoOpStatisticsCollector implements StatisticsCollector {

    private static final Statistics ZERO_STATS = new Statistics();

    @Override
    public <K> void incrementLoadCount(@Nullable IncrementLoadCountStatisticsContext<K> context) {
    }

    @Deprecated
    @Override
    public void incrementLoadCount() {
        incrementLoadCount(null);
    }

    @Override
    public <K> void incrementLoadErrorCount(@Nullable IncrementLoadErrorCountStatisticsContext<K> context) {
    }

    @Deprecated
    @Override
    public void incrementLoadErrorCount() {
        incrementLoadErrorCount(null);
    }

    @Override
    public <K> void incrementBatchLoadCountBy(long delta, @Nullable IncrementBatchLoadCountByStatisticsContext<K> context) {
    }

    @Deprecated
    @Override
    public void incrementBatchLoadCountBy(long delta) {
         incrementBatchLoadCountBy(delta, null);
    }

    @Override
    public <K> void incrementBatchLoadExceptionCount(@Nullable IncrementBatchLoadExceptionCountStatisticsContext<K> context) {

    }

    @Deprecated
    @Override
    public void incrementBatchLoadExceptionCount() {
        incrementBatchLoadExceptionCount(null);
    }

    @Override
    public <K> void incrementCacheHitCount(@Nullable IncrementCacheHitCountStatisticsContext<K> context) {
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

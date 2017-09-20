package org.dataloader.stats;

/**
 * A statistics collector that does nothing
 */
public class NoOpStatisticsCollector implements StatisticsCollector {

    private static final StatisticsImpl ZERO_STATS = new StatisticsImpl();

    @Override
    public long incrementLoadCount() {
        return 0;
    }

    @Override
    public long incrementLoadErrorCount() {
        return 0;
    }

    @Override
    public long incrementBatchLoadCountBy(long delta) {
        return 0;
    }

    @Override
    public long incrementBatchLoadExceptionCount() {
        return 0;
    }

    @Override
    public long incrementCacheHitCount() {
        return 0;
    }

    @Override
    public Statistics getStatistics() {
        return ZERO_STATS;
    }
}

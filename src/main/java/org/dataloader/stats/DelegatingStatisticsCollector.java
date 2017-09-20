package org.dataloader.stats;

import static org.dataloader.impl.Assertions.nonNull;

/**
 * This statistics collector keeps dataloader statistics AND also calls the delegate
 * collector at the same time.  This allows you to keep a specific set of statistics
 * and also delegate the calls onto another collector.
 */
public class DelegatingStatisticsCollector implements StatisticsCollector {

    private final StatisticsCollector collector = new SimpleStatisticsCollector();
    private final StatisticsCollector delegateCollector;

    /**
     * @param delegateCollector a non null delegate collector
     */
    public DelegatingStatisticsCollector(StatisticsCollector delegateCollector) {
        this.delegateCollector = nonNull(delegateCollector);
    }

    @Override
    public long incrementLoadCount() {
        delegateCollector.incrementLoadCount();
        return collector.incrementLoadCount();
    }

    @Override
    public long incrementBatchLoadCount() {
        delegateCollector.incrementBatchLoadCount();
        return collector.incrementBatchLoadCount();
    }

    @Override
    public long incrementCacheHitCount() {
        delegateCollector.incrementCacheHitCount();
        return collector.incrementCacheHitCount();
    }

    @Override
    public long incrementLoadErrorCount() {
        delegateCollector.incrementLoadErrorCount();
        return collector.incrementLoadErrorCount();
    }

    @Override
    public long incrementBatchLoadExceptionCount() {
        delegateCollector.incrementBatchLoadExceptionCount();
        return collector.incrementBatchLoadExceptionCount();
    }

    /**
     * @return the statistics of the this collector (and not its delegate)
     */
    @Override
    public Statistics getStatistics() {
        return collector.getStatistics();
    }

    /**
     * @return the statistics of the delegate
     */
    public Statistics getDelegateStatistics() {
        return delegateCollector.getStatistics();
    }

}

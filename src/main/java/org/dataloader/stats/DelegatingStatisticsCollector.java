package org.dataloader.stats;

import org.dataloader.stats.context.IncrementBatchLoadCountByStatisticsContext;
import org.dataloader.stats.context.IncrementBatchLoadExceptionCountStatisticsContext;
import org.dataloader.stats.context.IncrementCacheHitCountStatisticsContext;
import org.dataloader.stats.context.IncrementLoadCountStatisticsContext;
import org.dataloader.stats.context.IncrementLoadErrorCountStatisticsContext;

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
    public <K> long incrementLoadCount(IncrementLoadCountStatisticsContext<K> context) {
        delegateCollector.incrementLoadCount(context);
        return collector.incrementLoadCount(context);
    }

    @Deprecated
    @Override
    public long incrementLoadCount() {
        return incrementLoadCount(null);
    }

    @Override
    public <K> long incrementLoadErrorCount(IncrementLoadErrorCountStatisticsContext<K> context) {
        delegateCollector.incrementLoadErrorCount(context);
        return collector.incrementLoadErrorCount(context);
    }

    @Deprecated
    @Override
    public long incrementLoadErrorCount() {
        return incrementLoadErrorCount(null);
    }

    @Override
    public <K> long incrementBatchLoadCountBy(long delta, IncrementBatchLoadCountByStatisticsContext<K> context) {
        delegateCollector.incrementBatchLoadCountBy(delta, context);
        return collector.incrementBatchLoadCountBy(delta, context);
    }

    @Deprecated
    @Override
    public long incrementBatchLoadCountBy(long delta) {
        return incrementBatchLoadCountBy(delta, null);
    }

    @Override
    public <K> long incrementBatchLoadExceptionCount(IncrementBatchLoadExceptionCountStatisticsContext<K> context) {
        delegateCollector.incrementBatchLoadExceptionCount(context);
        return collector.incrementBatchLoadExceptionCount(context);
    }

    @Deprecated
    @Override
    public long incrementBatchLoadExceptionCount() {
        return incrementBatchLoadExceptionCount(null);
    }

    @Override
    public <K> long incrementCacheHitCount(IncrementCacheHitCountStatisticsContext<K> context) {
        delegateCollector.incrementCacheHitCount(context);
        return collector.incrementCacheHitCount(context);
    }

    @Deprecated
    @Override
    public long incrementCacheHitCount() {
        return incrementCacheHitCount(null);
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

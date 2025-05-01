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
    public <K> void incrementLoadCount(IncrementLoadCountStatisticsContext<K> context) {
        delegateCollector.incrementLoadCount(context);
        collector.incrementLoadCount(context);
    }

    @Deprecated
    @Override
    public void incrementLoadCount() {
        incrementLoadCount(null);
    }

    @Override
    public <K> void incrementLoadErrorCount(IncrementLoadErrorCountStatisticsContext<K> context) {
        delegateCollector.incrementLoadErrorCount(context);
        collector.incrementLoadErrorCount(context);
    }

    @Deprecated
    @Override
    public void incrementLoadErrorCount() {
        incrementLoadErrorCount(null);
    }

    @Override
    public <K> void incrementBatchLoadCountBy(long delta, IncrementBatchLoadCountByStatisticsContext<K> context) {
        delegateCollector.incrementBatchLoadCountBy(delta, context);
        collector.incrementBatchLoadCountBy(delta, context);
    }

    @Deprecated
    @Override
    public void incrementBatchLoadCountBy(long delta) {
        incrementBatchLoadCountBy(delta, null);
    }

    @Override
    public <K> void incrementBatchLoadExceptionCount(IncrementBatchLoadExceptionCountStatisticsContext<K> context) {
        delegateCollector.incrementBatchLoadExceptionCount(context);
        collector.incrementBatchLoadExceptionCount(context);
    }

    @Deprecated
    @Override
    public void incrementBatchLoadExceptionCount() {
        incrementBatchLoadExceptionCount(null);
    }

    @Override
    public <K> void incrementCacheHitCount(IncrementCacheHitCountStatisticsContext<K> context) {
        delegateCollector.incrementCacheHitCount(context);
        collector.incrementCacheHitCount(context);
    }

    @Deprecated
    @Override
    public void incrementCacheHitCount() {
        incrementCacheHitCount(null);
    }

    /**
     * @return the statistics of the collector (and not its delegate)
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

package org.dataloader.stats;

import org.dataloader.annotations.PublicSpi;
import org.dataloader.stats.context.IncrementBatchLoadCountByStatisticsContext;
import org.dataloader.stats.context.IncrementBatchLoadExceptionCountStatisticsContext;
import org.dataloader.stats.context.IncrementCacheHitCountStatisticsContext;
import org.dataloader.stats.context.IncrementLoadCountStatisticsContext;
import org.dataloader.stats.context.IncrementLoadErrorCountStatisticsContext;

/**
 * This allows statistics to be collected for {@link org.dataloader.DataLoader} operations
 */
@PublicSpi
public interface StatisticsCollector {

    /**
     * Called to increment the number of loads
     *
     * @param <K> the class of the key in the data loader
     * @param context the context containing metadata of the data loader invocation
     */
    default <K> void incrementLoadCount(IncrementLoadCountStatisticsContext<K> context) {
        incrementLoadCount();
    }

    /**
     * Called to increment the number of loads
     *
     * @deprecated use {@link #incrementLoadCount(IncrementLoadCountStatisticsContext)}
     */
    @Deprecated
    void incrementLoadCount();

    /**
     * Called to increment the number of loads that resulted in an object deemed in error
     *
     * @param <K> the class of the key in the data loader
     * @param context the context containing metadata of the data loader invocation
     *
     */
    default <K> void incrementLoadErrorCount(IncrementLoadErrorCountStatisticsContext<K> context) {
        incrementLoadErrorCount();
    }

    /**
     * Called to increment the number of loads that resulted in an object deemed in error
     *
     * @deprecated use {@link #incrementLoadErrorCount(IncrementLoadErrorCountStatisticsContext)}
     */
    @Deprecated
    void incrementLoadErrorCount();

    /**
     * Called to increment the number of batch loads
     *
     * @param <K> the class of the key in the data loader
     * @param delta how much to add to the count
     * @param context the context containing metadata of the data loader invocation
     */
    default <K> void  incrementBatchLoadCountBy(long delta, IncrementBatchLoadCountByStatisticsContext<K> context) {
        incrementBatchLoadCountBy(delta);
    }

    /**
     * Called to increment the number of batch loads
     *
     * @param delta how much to add to the count
     *
     * @deprecated use {@link #incrementBatchLoadCountBy(long, IncrementBatchLoadCountByStatisticsContext)}
     */
    @Deprecated
    void incrementBatchLoadCountBy(long delta);

    /**
     * Called to increment the number of batch loads exceptions
     *
     * @param <K> the class of the key in the data loader
     * @param context the context containing metadata of the data loader invocation
     */
    default <K> void incrementBatchLoadExceptionCount(IncrementBatchLoadExceptionCountStatisticsContext<K> context) {
        incrementBatchLoadExceptionCount();
    }

    /**
     * Called to increment the number of batch loads exceptions
     *
     * @deprecated use {@link #incrementBatchLoadExceptionCount(IncrementBatchLoadExceptionCountStatisticsContext)}
     */
    @Deprecated
    void incrementBatchLoadExceptionCount();

    /**
     * Called to increment the number of cache hits
     *
     * @param <K> the class of the key in the data loader
     * @param context the context containing metadata of the data loader invocation
     */
    default <K> void incrementCacheHitCount(IncrementCacheHitCountStatisticsContext<K> context) {
        incrementCacheHitCount();
    }

    /**
     * Called to increment the number of cache hits
     *
     * @deprecated use {@link #incrementCacheHitCount(IncrementCacheHitCountStatisticsContext)}
     */
    @Deprecated
    void incrementCacheHitCount();

    /**
     * @return the statistics that have been gathered to this point in time
     */
    Statistics getStatistics();
}

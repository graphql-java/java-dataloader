package org.dataloader.stats;

/**
 * This can collect statistics per thread as well as in an overall sense.  This allows you to snapshot stats for a web request say
 * as well as all requests.
 * <p>
 * You will want to call {@link #resetThread()} to clean up the thread local aspects of this object per request thread.
 * <p>
 * ThreadLocals have their place in the Java world but be careful on how you use them.  If you don't clean them up on "request boundaries"
 * then you WILL have misleading statistics.
 *
 * @see org.dataloader.stats.StatisticsCollector
 */
public class ThreadLocalStatisticsCollector implements StatisticsCollector {

    private static final ThreadLocal<SimpleStatisticsCollector> collector = ThreadLocal.withInitial(SimpleStatisticsCollector::new);

    private final SimpleStatisticsCollector overallCollector = new SimpleStatisticsCollector();

    /**
     * Removes the underlying thread local value for this current thread.  This is a way to reset the thread local
     * values for the current thread and start afresh
     *
     * @return this collector for fluent coding
     */
    public ThreadLocalStatisticsCollector resetThread() {
        collector.remove();
        return this;
    }

    @Override
    public long incrementLoadCount() {
        overallCollector.incrementLoadCount();
        return collector.get().incrementLoadCount();
    }

    @Override
    public long incrementBatchLoadCountBy(long delta) {
        overallCollector.incrementBatchLoadCountBy(delta);
        return collector.get().incrementBatchLoadCountBy(delta);
    }

    @Override
    public long incrementCacheHitCount() {
        overallCollector.incrementCacheHitCount();
        return collector.get().incrementCacheHitCount();
    }

    @Override
    public long incrementLoadErrorCount() {
        overallCollector.incrementLoadErrorCount();
        return collector.get().incrementLoadErrorCount();
    }

    @Override
    public long incrementBatchLoadExceptionCount() {
        overallCollector.incrementBatchLoadExceptionCount();
        return collector.get().incrementBatchLoadExceptionCount();
    }

    /**
     * This returns the statistics for this thread.
     *
     * @return this thread's statistics
     */
    @Override
    public Statistics getStatistics() {
        return collector.get().getStatistics();
    }

    /**
     * This returns the overall statistics, that is not per thread but for the life of this object
     *
     * @return overall statistics
     */
    public Statistics getOverallStatistics() {
        return overallCollector.getStatistics();
    }

    @Override
    public String toString() {
        return "ThreadLocalStatisticsCollector{" +
                "thread=" + getStatistics().toString() +
                "overallCollector=" + overallCollector.getStatistics().toString() +
                '}';
    }
}

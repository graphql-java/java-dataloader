package org.dataloader.stats;

public class StatisticsImpl implements Statistics {

    private final long loadCount;
    private final long batchLoadCount;
    private final long cacheHitCount;
    private final long batchLoadExceptionCount;
    private final long loadErrorCount;

    public StatisticsImpl(long loadCount, long batchLoadCount, long cacheHitCount, long batchLoadExceptionCount, long loadErrorCount) {
        this.loadCount = loadCount;
        this.batchLoadCount = batchLoadCount;
        this.cacheHitCount = cacheHitCount;
        this.batchLoadExceptionCount = batchLoadExceptionCount;
        this.loadErrorCount = loadErrorCount;
    }

    @Override
    public long getLoadCount() {
        return loadCount;
    }

    @Override
    public long getBatchLoadCount() {
        return batchLoadCount;
    }

    @Override
    public float getBatchLoadRatio() {
        return ratio(batchLoadCount, loadCount);
    }

    private float ratio(long numerator, long denominator) {
        return denominator == 0 ? 0f : ((float) numerator) / ((float) denominator);
    }

    @Override
    public long getCacheHitCount() {
        return cacheHitCount;
    }

    @Override
    public long getCacheMissCount() {
        return loadCount - cacheHitCount;
    }

    @Override
    public float getCacheHitRatio() {
        return ratio(cacheHitCount, loadCount);
    }

    @Override
    public long getBatchLoadExceptionCount() {
        return batchLoadExceptionCount;
    }

    @Override
    public long getLoadErrorCount() {
        return loadErrorCount;
    }

    @Override
    public float getBatchLoadExceptionRatio() {
        return ratio(batchLoadExceptionCount, loadCount);
    }

    @Override
    public float getLoadErrorRatio() {
        return ratio(loadErrorCount, loadCount);
    }

    @Override
    public String toString() {
        return "StatisticsImpl{" +
                "loadCount=" + loadCount +
                ", batchLoadCount=" + batchLoadCount +
                ", cacheHitCount=" + cacheHitCount +
                ", batchLoadExceptionCount=" + batchLoadExceptionCount +
                ", loadErrorCount=" + loadErrorCount +
                '}';
    }
}

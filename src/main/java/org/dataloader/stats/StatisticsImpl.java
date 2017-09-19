package org.dataloader.stats;

public class StatisticsImpl implements Statistics {

    private final long loadCount;
    private final long batchLoadCount;
    private final long cacheHitCount;

    public StatisticsImpl(long loadCount, long batchLoadCount, long cacheHitCount) {
        this.loadCount = loadCount;
        this.batchLoadCount = batchLoadCount;
        this.cacheHitCount = cacheHitCount;
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
    public String toString() {
        return "StatisticsImpl{" +
                "loadCount=" + loadCount +
                ", batchLoadCount=" + batchLoadCount +
                ", cacheHitCount=" + cacheHitCount +
                '}';
    }
}

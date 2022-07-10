package org.dataloader.stats.context;

public class IncrementLoadErrorCountStatisticsContext<K> {

    private final K key;
    private final Object callContext;

    public IncrementLoadErrorCountStatisticsContext(K key, Object callContext) {
        this.key = key;
        this.callContext = callContext;
    }

    public K getKey() {
        return key;
    }

    public Object getCallContext() {
        return callContext;
    }
}

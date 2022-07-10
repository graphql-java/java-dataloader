package org.dataloader.stats.context;

import java.util.List;

public class IncrementBatchLoadExceptionCountStatisticsContext<K> {

    private final List<K> keys;
    private final List<Object> callContexts;

    public IncrementBatchLoadExceptionCountStatisticsContext(List<K> keys, List<Object> callContexts) {
        this.keys = keys;
        this.callContexts = callContexts;
    }

    public List<K> getKeys() {
        return keys;
    }

    public List<Object> getCallContexts() {
        return callContexts;
    }
}

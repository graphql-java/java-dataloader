package org.dataloader.stats.context;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NullMarked
public class IncrementBatchLoadExceptionCountStatisticsContext<K> {

    private final List<K> keys;
    private final List<@Nullable Object> callContexts;

    public IncrementBatchLoadExceptionCountStatisticsContext(List<K> keys, List<@Nullable Object> callContexts) {
        this.keys = keys;
        this.callContexts = callContexts;
    }

    public List<K> getKeys() {
        return keys;
    }

    public List<@Nullable Object> getCallContexts() {
        return callContexts;
    }
}

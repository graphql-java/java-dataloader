package org.dataloader;

import java.time.Clock;

public class ClockDataLoader<K, V> extends DataLoader<K, V> {

    public ClockDataLoader(Object batchLoadFunction, Clock clock) {
        this(batchLoadFunction, null, clock);
    }

    public ClockDataLoader(Object batchLoadFunction, DataLoaderOptions options, Clock clock) {
        super(null, batchLoadFunction, options, clock);
    }

}

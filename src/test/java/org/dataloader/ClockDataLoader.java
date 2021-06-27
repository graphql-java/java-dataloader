package org.dataloader;

import java.time.Clock;

public class ClockDataLoader<K, V> extends DataLoader<K, V> {

    ClockDataLoader(Object batchLoadFunction, Clock clock) {
        this(batchLoadFunction, null, clock);
    }

    ClockDataLoader(Object batchLoadFunction, DataLoaderOptions options, Clock clock) {
        super(batchLoadFunction, options, clock);
    }

}

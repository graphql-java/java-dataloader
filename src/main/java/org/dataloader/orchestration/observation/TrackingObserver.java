package org.dataloader.orchestration.observation;

import org.dataloader.DataLoader;
import org.dataloader.annotations.PublicSpi;

/**
 * This callback is invoked when the {@link org.dataloader.orchestration.Orchestrator} starts execution and then
 * as each {@link DataLoader} is invoked and then again when it completes
 */
@PublicSpi
public interface TrackingObserver {
    void onStart(Tracker tracker);

    void onLoad(Tracker tracker, int stepIndex, DataLoader<?, ?> dl);

    // TODO - should this have an exception should it fail in CF terms ???
    void onLoadComplete(Tracker tracker, int stepIndex, DataLoader<?, ?> dl, Throwable throwable);
}

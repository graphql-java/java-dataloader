package org.dataloader;

import org.dataloader.annotations.PublicApi;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * When a DataLoader is dispatched this object holds the promised results and also the count of key asked for
 * via methods like {@link org.dataloader.DataLoader#load(Object)} or {@link org.dataloader.DataLoader#loadMany(java.util.List)}
 *
 * @param <T> for two
 */
@PublicApi
public class DispatchResult<T> {
    private final CompletableFuture<List<T>> futureList;
    private final int keysCount;
    private final boolean wasDispatched;

    public DispatchResult(CompletableFuture<List<T>> futureList, int keysCount) {
        this(futureList, keysCount, true);
    }

    public DispatchResult(CompletableFuture<List<T>> futureList, int keysCount, boolean wasDispatched) {
        this.futureList = futureList;
        this.keysCount = keysCount;
        this.wasDispatched = wasDispatched;
    }

    public CompletableFuture<List<T>> getPromisedResults() {
        return futureList;
    }

    public int getKeysCount() {
        return keysCount;
    }

    /**
     * If the {@link org.dataloader.registries.DispatchPredicate} associated with the dataloader
     * returns false, then the call to dispatch was not performed and this will return false.
     *
     * @return true of the dispatch call was actually made or false if it was not
     */
    public boolean wasDispatched() {
        return wasDispatched;
    }
}

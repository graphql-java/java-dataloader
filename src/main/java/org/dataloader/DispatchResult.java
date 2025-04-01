package org.dataloader;

import org.dataloader.annotations.PublicApi;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * When a DataLoader is dispatched this object holds the promised results and also the count of key asked for
 * via methods like {@link org.dataloader.DataLoader#load(Object)} or {@link org.dataloader.DataLoader#loadMany(java.util.List)}
 *
 * @param <T> for two
 */
@PublicApi
@NullMarked
public class DispatchResult<T> {
    private final CompletableFuture<List<T>> futureList;
    private final int keysCount;

    public DispatchResult(CompletableFuture<List<T>> futureList, int keysCount) {
        this.futureList = futureList;
        this.keysCount = keysCount;
    }

    public CompletableFuture<List<T>> getPromisedResults() {
        return futureList;
    }

    public int getKeysCount() {
        return keysCount;
    }
}

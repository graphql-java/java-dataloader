package org.dataloader;

import org.dataloader.annotations.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;


/**
 * An interface to implement to allow for custom dispatch strategies when executing {@link DataLoader}s
 */
@NullMarked
@PublicApi
public interface DispatchStrategy {

    /**
     * A {@link DispatchStrategy} that does nothing
     */
    DispatchStrategy NO_OP = new DispatchStrategy() {
    };

    /**
     * Lifecycle method called when the registry is created that this dispatch strategy is attached to
     * @param registry the {@link DataLoaderRegistry} this dispatch strategy is attached to
     */
    default void onRegistryCreation(DataLoaderRegistry registry) {

    }

    /**
     * Called when a {@link DataLoader#load(Object)} is called on a dataloader
     */
    default void loadCalled(DataLoader<?, ?> dataLoader, Object key, @Nullable Object loadContext, CompletableFuture<?> result) {

    }

    /**
     * Called when a {@link DataLoader#load(Object)} is executed and completed on a dataloader
     */
    default void loadCompleted(DataLoader<?, ?> dataLoader, @Nullable Object result, @Nullable Throwable error) {

    }
}

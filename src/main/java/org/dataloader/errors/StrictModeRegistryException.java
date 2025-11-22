package org.dataloader.errors;

import org.dataloader.annotations.PublicApi;

/**
 * An exception that is thrown when {@link org.dataloader.DataLoaderRegistry.Builder#strictMode(boolean)} is true and multiple
 * DataLoaders are registered to the same key.
 */
@PublicApi
public class StrictModeRegistryException extends RuntimeException {
    public StrictModeRegistryException(String msg) {
        super(msg);
    }
}

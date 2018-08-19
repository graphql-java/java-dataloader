package org.dataloader;

/**
 * A BatchContextProvider is used by the {@link org.dataloader.DataLoader} code to
 * provide context to the {@link org.dataloader.BatchLoader} call.  A common use
 * case is for propagating user security credentials or database connection parameters.
 */
public interface BatchContextProvider {
    /**
     * @return a context object that may be needed in batch calls
     */
    Object get();
}
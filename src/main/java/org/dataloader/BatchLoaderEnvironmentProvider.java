package org.dataloader;

import org.dataloader.annotations.PublicSpi;
import org.jspecify.annotations.NullMarked;

/**
 * A BatchLoaderEnvironmentProvider is used by the {@link org.dataloader.DataLoader} code to
 * provide {@link org.dataloader.BatchLoaderEnvironment} calling context to
 * the {@link org.dataloader.BatchLoader} call.  A common use
 * case is for propagating user security credentials or database connection parameters.
 */
@PublicSpi
@NullMarked
public interface BatchLoaderEnvironmentProvider {
    /**
     * @return a {@link org.dataloader.BatchLoaderEnvironment} that may be needed in batch calls
     */
    BatchLoaderEnvironment get();
}

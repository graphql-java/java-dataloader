package org.dataloader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This allows data loaders to be registered together into a single place so
 * they can be dispatched as one.
 */
public class DataLoaderRegistry {
    private final List<DataLoader<?, ?>> dataLoaders = new CopyOnWriteArrayList<>();

    /**
     * @return the currently registered data loaders
     */
    public List<DataLoader<?, ?>> getDataLoaders() {
        return new ArrayList<>(dataLoaders);
    }

    /**
     * This will register a new dataloader
     *
     * @param dataLoader the data loader to register
     *
     * @return this registry
     */
    public DataLoaderRegistry register(DataLoader<?, ?> dataLoader) {
        if (!dataLoaders.contains(dataLoader)) {
            dataLoaders.add(dataLoader);
        }
        return this;
    }

    /**
     * This will unregister a new dataloader
     *
     * @param dataLoader the data loader to unregister
     *
     * @return this registry
     */
    public DataLoaderRegistry unregister(DataLoader<?, ?> dataLoader) {
        dataLoaders.remove(dataLoader);
        return this;
    }

    /**
     * This will called {@link org.dataloader.DataLoader#dispatch()} on each of the registered
     * {@link org.dataloader.DataLoader}s
     */
    public void dispatchAll() {
        dataLoaders.forEach(DataLoader::dispatch);
    }
}

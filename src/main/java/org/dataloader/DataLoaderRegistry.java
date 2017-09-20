package org.dataloader;

import org.dataloader.stats.Statistics;
import org.dataloader.stats.StatisticsImpl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This allows data loaders to be registered together into a single place so
 * they can be dispatched as one.  It also allows you to retrieve data loaders by
 * name from a central place
 */
public class DataLoaderRegistry {
    private final Map<String, DataLoader<?, ?>> dataLoaders = new ConcurrentHashMap<>();

    /**
     * This will register a new dataloader
     *
     * @param key        the key to put the data loader under
     * @param dataLoader the data loader to register
     *
     * @return this registry
     */
    public DataLoaderRegistry register(String key, DataLoader<?, ?> dataLoader) {
        dataLoaders.put(key, dataLoader);
        return this;
    }

    /**
     * This will combine all the current data loaders in this registry and all the data loaders from the specified registry
     * and return a new combined registry
     *
     * @param registry the registry to combine into this registry
     *
     * @return a new combined registry
     */
    public DataLoaderRegistry combine(DataLoaderRegistry registry) {
        DataLoaderRegistry combined = new DataLoaderRegistry();

        this.dataLoaders.forEach(combined::register);
        registry.dataLoaders.forEach(combined::register);
        return combined;
    }

    /**
     * @return the currently registered data loaders
     */
    public List<DataLoader<?, ?>> getDataLoaders() {
        return new ArrayList<>(dataLoaders.values());
    }

    /**
     * This will unregister a new dataloader
     *
     * @param key the key of the data loader to unregister
     *
     * @return this registry
     */
    public DataLoaderRegistry unregister(String key) {
        dataLoaders.remove(key);
        return this;
    }

    /**
     * Returns the dataloader that was registered under the specified key
     *
     * @param key the key of the data loader
     * @param <K> the type of keys
     * @param <V> the type of values
     *
     * @return a data loader or null if its not present
     */
    @SuppressWarnings("unchecked")
    public <K, V> DataLoader<K, V> getDataLoader(String key) {
        return (DataLoader<K, V>) dataLoaders.get(key);
    }

    /**
     * @return the keys of the data loaders in this registry
     */
    public Set<String> getKeys() {
        return new HashSet<>(dataLoaders.keySet());
    }

    /**
     * This will called {@link org.dataloader.DataLoader#dispatch()} on each of the registered
     * {@link org.dataloader.DataLoader}s
     */
    public void dispatchAll() {
        getDataLoaders().forEach(DataLoader::dispatch);
    }

    /**
     * @return a combined set of statistics for all data loaders in this registry presented
     * as the sum of all their statistics
     */
    public Statistics getStatistics() {
        Statistics stats = new StatisticsImpl();
        for (DataLoader<?, ?> dataLoader : dataLoaders.values()) {
            stats = stats.combine(dataLoader.getStatistics());
        }
        return stats;
    }
}

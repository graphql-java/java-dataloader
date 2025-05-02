package org.dataloader;

import org.dataloader.annotations.PublicApi;
import org.dataloader.instrumentation.ChainedDataLoaderInstrumentation;
import org.dataloader.instrumentation.DataLoaderInstrumentation;
import org.dataloader.instrumentation.DataLoaderInstrumentationHelper;
import org.dataloader.stats.Statistics;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.dataloader.impl.Assertions.assertState;

/**
 * This allows data loaders to be registered together into a single place, so
 * they can be dispatched as one.  It also allows you to retrieve data loaders by
 * name from a central place.
 * <p>
 * Notes on {@link DataLoaderInstrumentation} : A {@link DataLoaderRegistry} can have an instrumentation
 * associated with it.  As each {@link DataLoader} is added to the registry, the {@link DataLoaderInstrumentation}
 * of the registry is applied to that {@link DataLoader}.
 * <p>
 * The {@link DataLoader} is changed and hence the object in the registry is not the
 * same one as was originally registered.  So you MUST get access to the {@link DataLoader} via {@link DataLoaderRegistry#getDataLoader(String)} methods
 * and not use the original {@link DataLoader} object.
 * <p>
 * If the {@link DataLoader} has no {@link DataLoaderInstrumentation} then the registry one is added to it.  If it does have one already
 * then a {@link ChainedDataLoaderInstrumentation} is created with the registry {@link DataLoaderInstrumentation} in it first and then any other
 * {@link DataLoaderInstrumentation}s added after that.  If the registry {@link DataLoaderInstrumentation} instance and {@link DataLoader} {@link DataLoaderInstrumentation} instance
 * are the same object, then nothing is changed, since the same instrumentation code is being run.
 */
@PublicApi
@NullMarked
public class DataLoaderRegistry {
    protected final Map<String, DataLoader<?, ?>> dataLoaders;
    protected final @Nullable DataLoaderInstrumentation instrumentation;


    public DataLoaderRegistry() {
        this(new ConcurrentHashMap<>(), null);
    }

    private DataLoaderRegistry(Builder builder) {
        this(builder.dataLoaders, builder.instrumentation);
    }

    protected DataLoaderRegistry(Map<String, DataLoader<?, ?>> dataLoaders, @Nullable DataLoaderInstrumentation instrumentation) {
        this.dataLoaders = instrumentDLs(dataLoaders, instrumentation);
        this.instrumentation = instrumentation;
    }

    private Map<String, DataLoader<?, ?>> instrumentDLs(Map<String, DataLoader<?, ?>> incomingDataLoaders, @Nullable DataLoaderInstrumentation registryInstrumentation) {
        Map<String, DataLoader<?, ?>> dataLoaders = new ConcurrentHashMap<>(incomingDataLoaders);
        if (registryInstrumentation != null) {
            dataLoaders.replaceAll((k, existingDL) -> nameAndInstrumentDL(k, registryInstrumentation, existingDL));
        }
        return dataLoaders;
    }

    /**
     * Can be called to tweak a {@link DataLoader} so that it has the registry {@link DataLoaderInstrumentation} added as the first one.
     *
     * @param key                     the key used to register the data loader
     * @param registryInstrumentation the common registry {@link DataLoaderInstrumentation}
     * @param existingDL              the existing data loader
     * @return a new {@link DataLoader} or the same one if there is nothing to change
     */
    private static DataLoader<?, ?> nameAndInstrumentDL(String key, @Nullable DataLoaderInstrumentation registryInstrumentation, DataLoader<?, ?> existingDL) {
        existingDL = checkAndSetName(key, existingDL);

        if (registryInstrumentation == null) {
            return existingDL;
        }
        DataLoaderOptions options = existingDL.getOptions();
        DataLoaderInstrumentation existingInstrumentation = options.getInstrumentation();
        // if they have any instrumentations then add to it
        if (existingInstrumentation != null) {
            if (existingInstrumentation == registryInstrumentation) {
                // nothing to change
                return existingDL;
            }
            if (existingInstrumentation == DataLoaderInstrumentationHelper.NOOP_INSTRUMENTATION) {
                // replace it with the registry one
                return mkInstrumentedDataLoader(existingDL, options, registryInstrumentation);
            }
            if (existingInstrumentation instanceof ChainedDataLoaderInstrumentation) {
                // avoids calling a chained inside a chained
                DataLoaderInstrumentation newInstrumentation = ((ChainedDataLoaderInstrumentation) existingInstrumentation).prepend(registryInstrumentation);
                return mkInstrumentedDataLoader(existingDL, options, newInstrumentation);
            } else {
                DataLoaderInstrumentation newInstrumentation = new ChainedDataLoaderInstrumentation().add(registryInstrumentation).add(existingInstrumentation);
                return mkInstrumentedDataLoader(existingDL, options, newInstrumentation);
            }
        } else {
            return mkInstrumentedDataLoader(existingDL, options, registryInstrumentation);
        }
    }

    private static DataLoader<?, ?> checkAndSetName(String key, DataLoader<?, ?> dataLoader) {
        if (dataLoader.getName() == null) {
            return dataLoader.transform(b -> b.name(key));
        }
        assertState(key.equals(dataLoader.getName()),
                () -> String.format("Data loader name '%s' is not the same as registered key '%s'", dataLoader.getName(), key));
        return dataLoader;
    }

    private static DataLoader<?, ?> mkInstrumentedDataLoader(DataLoader<?, ?> existingDL, DataLoaderOptions options, DataLoaderInstrumentation newInstrumentation) {
        return existingDL.transform(builder -> builder.options(setInInstrumentation(options, newInstrumentation)));
    }

    private static DataLoaderOptions setInInstrumentation(DataLoaderOptions options, DataLoaderInstrumentation newInstrumentation) {
        return options.transform(optionsBuilder -> optionsBuilder.setInstrumentation(newInstrumentation));
    }

    /**
     * @return the {@link DataLoaderInstrumentation} associated with this registry which can be null
     */
    public @Nullable DataLoaderInstrumentation getInstrumentation() {
        return instrumentation;
    }

    /**
     * This will register a new dataloader
     *
     * @param key        the key to put the data loader under
     * @param dataLoader the data loader to register
     * @return this registry
     */
    public DataLoaderRegistry register(String key, DataLoader<?, ?> dataLoader) {
        dataLoaders.put(key, nameAndInstrumentDL(key, instrumentation, dataLoader));
        return this;
    }

    /**
     * This will register a new dataloader and then return it.  It might have been wrapped into a new instance
     * because of the registry instrumentation for example.
     *
     * @param key        the key to put the data loader under
     * @param dataLoader the data loader to register
     * @return the data loader instance that was registered
     */
    public <K, V> DataLoader<K, V> registerAndGet(String key, DataLoader<?, ?> dataLoader) {
        dataLoaders.put(key, nameAndInstrumentDL(key, instrumentation, dataLoader));
        return getDataLoader(key);
    }

    /**
     * Computes a data loader if absent or return it if it was
     * already registered at that key.
     * <p>
     * Note: The entire method invocation is performed atomically,
     * so the function is applied at most once per key.
     *
     * @param key             the key of the data loader
     * @param mappingFunction the function to compute a data loader
     * @param <K>             the type of keys
     * @param <V>             the type of values
     * @return a data loader
     */
    @SuppressWarnings("unchecked")
    public <K, V> DataLoader<K, V> computeIfAbsent(final String key,
                                                   final Function<String, DataLoader<?, ?>> mappingFunction) {
        return (DataLoader<K, V>) dataLoaders.computeIfAbsent(key, (k) -> {
            DataLoader<?, ?> dl = mappingFunction.apply(k);
            return nameAndInstrumentDL(key, instrumentation, dl);
        });
    }

    /**
     * This will combine all the current data loaders in this registry and all the data loaders from the specified registry
     * and return a new combined registry
     *
     * @param registry the registry to combine into this registry
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
     * @return the currently registered data loaders as a map
     */
    public Map<String, DataLoader<?, ?>> getDataLoadersMap() {
        return new LinkedHashMap<>(dataLoaders);
    }

    /**
     * This will unregister a new dataloader
     *
     * @param key the key of the data loader to unregister
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
     * This will be called {@link org.dataloader.DataLoader#dispatch()} on each of the registered
     * {@link org.dataloader.DataLoader}s
     */
    public void dispatchAll() {
        getDataLoaders().forEach(DataLoader::dispatch);
    }

    /**
     * Similar to {@link DataLoaderRegistry#dispatchAll()}, this calls {@link org.dataloader.DataLoader#dispatch()} on
     * each of the registered {@link org.dataloader.DataLoader}s, but returns the number of dispatches.
     *
     * @return total number of entries that were dispatched from registered {@link org.dataloader.DataLoader}s.
     */
    public int dispatchAllWithCount() {
        int sum = 0;
        for (DataLoader<?, ?> dataLoader : getDataLoaders()) {
            sum += dataLoader.dispatchWithCounts().getKeysCount();
        }
        return sum;
    }

    /**
     * @return The sum of all batched key loads that need to be dispatched from all registered
     * {@link org.dataloader.DataLoader}s
     */
    public int dispatchDepth() {
        int totalDispatchDepth = 0;
        for (DataLoader<?, ?> dataLoader : getDataLoaders()) {
            totalDispatchDepth += dataLoader.dispatchDepth();
        }
        return totalDispatchDepth;
    }

    /**
     * @return a combined set of statistics for all data loaders in this registry presented
     * as the sum of all their statistics
     */
    public Statistics getStatistics() {
        Statistics stats = new Statistics();
        for (DataLoader<?, ?> dataLoader : dataLoaders.values()) {
            stats = stats.combine(dataLoader.getStatistics());
        }
        return stats;
    }

    /**
     * @return A builder of {@link DataLoaderRegistry}s
     */
    public static Builder newRegistry() {
        return new Builder();
    }

    public static class Builder {

        private final Map<String, DataLoader<?, ?>> dataLoaders = new HashMap<>();
        private @Nullable DataLoaderInstrumentation instrumentation;

        /**
         * This will register a new dataloader
         *
         * @param key        the key to put the data loader under
         * @param dataLoader the data loader to register
         * @return this builder for a fluent pattern
         */
        public Builder register(String key, DataLoader<?, ?> dataLoader) {
            dataLoaders.put(key, dataLoader);
            return this;
        }

        /**
         * This will combine the data loaders in this builder with the ones
         * from a previous {@link DataLoaderRegistry}
         *
         * @param otherRegistry the previous {@link DataLoaderRegistry}
         * @return this builder for a fluent pattern
         */
        public Builder registerAll(DataLoaderRegistry otherRegistry) {
            dataLoaders.putAll(otherRegistry.dataLoaders);
            return this;
        }

        public Builder instrumentation(DataLoaderInstrumentation instrumentation) {
            this.instrumentation = instrumentation;
            return this;
        }

        /**
         * @return the newly built {@link DataLoaderRegistry}
         */
        public DataLoaderRegistry build() {
            return new DataLoaderRegistry(this);
        }
    }
}

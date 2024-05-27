package org.dataloader;

import org.dataloader.annotations.PublicApi;

/**
 * A factory class to create {@link DataLoader}s
 */
@SuppressWarnings("unused")
@PublicApi
public class DataLoaderFactory {

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newDataLoader(BatchLoader<K, V> batchLoadFunction) {
        return newDataLoader(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newDataLoader(BatchLoader<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size) where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     * <p>
     * If it's important you to know the exact status of each item in a batch call and whether it threw exceptions then
     * you can use this form to create the data loader.
     * <p>
     * Using Try objects allows you to capture a value returned or an exception that might
     * have occurred trying to get a value. .
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newDataLoaderWithTry(BatchLoader<K, Try<V>> batchLoadFunction) {
        return newDataLoaderWithTry(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and with the provided options
     * where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     *
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    public static <K, V> DataLoader<K, V> newDataLoaderWithTry(BatchLoader<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newDataLoader(BatchLoaderWithContext<K, V> batchLoadFunction) {
        return newDataLoader(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newDataLoader(BatchLoaderWithContext<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size) where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     * <p>
     * If it's important you to know the exact status of each item in a batch call and whether it threw exceptions then
     * you can use this form to create the data loader.
     * <p>
     * Using Try objects allows you to capture a value returned or an exception that might
     * have occurred trying to get a value. .
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newDataLoaderWithTry(BatchLoaderWithContext<K, Try<V>> batchLoadFunction) {
        return newDataLoaderWithTry(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and with the provided options
     * where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     *
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    public static <K, V> DataLoader<K, V> newDataLoaderWithTry(BatchLoaderWithContext<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoader(MappedBatchLoader<K, V> batchLoadFunction) {
        return newMappedDataLoader(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoader(MappedBatchLoader<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size) where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     * <p>
     * If it's important you to know the exact status of each item in a batch call and whether it threw exceptions then
     * you can use this form to create the data loader.
     * <p>
     * Using Try objects allows you to capture a value returned or an exception that might
     * have occurred trying to get a value. .
     * <p>
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoaderWithTry(MappedBatchLoader<K, Try<V>> batchLoadFunction) {
        return newMappedDataLoaderWithTry(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and with the provided options
     * where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     *
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoaderWithTry(MappedBatchLoader<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified mapped batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoader(MappedBatchLoaderWithContext<K, V> batchLoadFunction) {
        return newMappedDataLoader(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoader(MappedBatchLoaderWithContext<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size) where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     * <p>
     * If it's important you to know the exact status of each item in a batch call and whether it threw exceptions then
     * you can use this form to create the data loader.
     * <p>
     * Using Try objects allows you to capture a value returned or an exception that might
     * have occurred trying to get a value. .
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoaderWithTry(MappedBatchLoaderWithContext<K, Try<V>> batchLoadFunction) {
        return newMappedDataLoaderWithTry(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and with the provided options
     * where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     *
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoaderWithTry(MappedBatchLoaderWithContext<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newPublisherDataLoader(BatchPublisher<K, V> batchLoadFunction) {
        return newPublisherDataLoader(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newPublisherDataLoader(BatchPublisher<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size) where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     * <p>
     * If it's important you to know the exact status of each item in a batch call and whether it threw exceptions then
     * you can use this form to create the data loader.
     * <p>
     * Using Try objects allows you to capture a value returned or an exception that might
     * have occurred trying to get a value. .
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newPublisherDataLoaderWithTry(BatchPublisher<K, Try<V>> batchLoadFunction) {
        return newPublisherDataLoaderWithTry(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and with the provided options
     * where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     *
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    public static <K, V> DataLoader<K, V> newPublisherDataLoaderWithTry(BatchPublisher<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newPublisherDataLoader(BatchPublisherWithContext<K, V> batchLoadFunction) {
        return newPublisherDataLoader(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newPublisherDataLoader(BatchPublisherWithContext<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size) where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     * <p>
     * If it's important you to know the exact status of each item in a batch call and whether it threw exceptions then
     * you can use this form to create the data loader.
     * <p>
     * Using Try objects allows you to capture a value returned or an exception that might
     * have occurred trying to get a value. .
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newPublisherDataLoaderWithTry(BatchPublisherWithContext<K, Try<V>> batchLoadFunction) {
        return newPublisherDataLoaderWithTry(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and with the provided options
     * where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     *
     * @see #newPublisherDataLoaderWithTry(BatchPublisher)
     */
    public static <K, V> DataLoader<K, V> newPublisherDataLoaderWithTry(BatchPublisherWithContext<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedPublisherDataLoader(MappedBatchPublisher<K, V> batchLoadFunction) {
        return newMappedPublisherDataLoader(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedPublisherDataLoader(MappedBatchPublisher<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size) where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     * <p>
     * If it's important you to know the exact status of each item in a batch call and whether it threw exceptions then
     * you can use this form to create the data loader.
     * <p>
     * Using Try objects allows you to capture a value returned or an exception that might
     * have occurred trying to get a value. .
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedPublisherDataLoaderWithTry(MappedBatchPublisher<K, Try<V>> batchLoadFunction) {
        return newMappedPublisherDataLoaderWithTry(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and with the provided options
     * where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     *
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    public static <K, V> DataLoader<K, V> newMappedPublisherDataLoaderWithTry(MappedBatchPublisher<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedPublisherDataLoader(MappedBatchPublisherWithContext<K, V> batchLoadFunction) {
        return newMappedPublisherDataLoader(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedPublisherDataLoader(MappedBatchPublisherWithContext<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size) where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     * <p>
     * If it's important you to know the exact status of each item in a batch call and whether it threw exceptions then
     * you can use this form to create the data loader.
     * <p>
     * Using Try objects allows you to capture a value returned or an exception that might
     * have occurred trying to get a value. .
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedPublisherDataLoaderWithTry(MappedBatchPublisherWithContext<K, Try<V>> batchLoadFunction) {
        return newMappedPublisherDataLoaderWithTry(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and with the provided options
     * where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     *
     * @see #newMappedPublisherDataLoaderWithTry(MappedBatchPublisher)
     */
    public static <K, V> DataLoader<K, V> newMappedPublisherDataLoaderWithTry(MappedBatchPublisherWithContext<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(batchLoadFunction, options);
    }

    static <K, V> DataLoader<K, V> mkDataLoader(Object batchLoadFunction, DataLoaderOptions options) {
        return new DataLoader<>(batchLoadFunction, options);
    }
}

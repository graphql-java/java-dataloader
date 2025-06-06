package org.dataloader;

import org.dataloader.annotations.PublicApi;
import org.jspecify.annotations.Nullable;

import static org.dataloader.impl.Assertions.nonNull;

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
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newDataLoader(BatchLoader<K, V> batchLoadFunction) {
        return newDataLoader(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param name              the name to use
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newDataLoader(String name, BatchLoader<K, V> batchLoadFunction) {
        return newDataLoader(name, batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newDataLoader(BatchLoader<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(null, batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param name              the name to use
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newDataLoader(String name, BatchLoader<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(nonNull(name), batchLoadFunction, options);
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
     * @return a new DataLoader
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    public static <K, V> DataLoader<K, V> newDataLoaderWithTry(BatchLoader<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(null, batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and with the provided options
     * where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * @param name              the name to use
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    public static <K, V> DataLoader<K, V> newDataLoaderWithTry(String name, BatchLoader<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(nonNull(name), batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
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
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newDataLoader(BatchLoaderWithContext<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(null, batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param name              the name to use
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newDataLoader(String name, BatchLoaderWithContext<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(nonNull(name), batchLoadFunction, options);
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
     * @return a new DataLoader
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    public static <K, V> DataLoader<K, V> newDataLoaderWithTry(BatchLoaderWithContext<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(null, batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and with the provided options
     * where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * @param name              the name to use
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    public static <K, V> DataLoader<K, V> newDataLoaderWithTry(String name, BatchLoaderWithContext<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(nonNull(name), batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
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
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoader(MappedBatchLoader<K, V> batchLoadFunction, @Nullable DataLoaderOptions options) {
        return mkDataLoader(null, batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoader(String name, MappedBatchLoader<K, V> batchLoadFunction, @Nullable DataLoaderOptions options) {
        return mkDataLoader(nonNull(name), batchLoadFunction, options);
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
     * @return a new DataLoader
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoaderWithTry(MappedBatchLoader<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(null, batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and with the provided options
     * where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * @param name              the name to use
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoaderWithTry(String name, MappedBatchLoader<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(nonNull(name), batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified mapped batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
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
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoader(MappedBatchLoaderWithContext<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(null, batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param name              the name to use
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoader(String name, MappedBatchLoaderWithContext<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(nonNull(name), batchLoadFunction, options);
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
     * @return a new DataLoader
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoaderWithTry(MappedBatchLoaderWithContext<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(null, batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and with the provided options
     * where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * @param name              the name to use
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    public static <K, V> DataLoader<K, V> newMappedDataLoaderWithTry(String name, MappedBatchLoaderWithContext<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(nonNull(name), batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
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
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newPublisherDataLoader(BatchPublisher<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(null, batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param name              the name to use
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newPublisherDataLoader(String name, BatchPublisher<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(nonNull(name), batchLoadFunction, options);
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
     * @return a new DataLoader
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    public static <K, V> DataLoader<K, V> newPublisherDataLoaderWithTry(BatchPublisher<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(null, batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and with the provided options
     * where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * @param name              the name to use
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    public static <K, V> DataLoader<K, V> newPublisherDataLoaderWithTry(String name, BatchPublisher<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(nonNull(name), batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
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
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newPublisherDataLoader(BatchPublisherWithContext<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(null, batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param name              the name to use
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newPublisherDataLoader(String name, BatchPublisherWithContext<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(nonNull(name), batchLoadFunction, options);
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
     * @return a new DataLoader
     * @see #newPublisherDataLoaderWithTry(BatchPublisher)
     */
    public static <K, V> DataLoader<K, V> newPublisherDataLoaderWithTry(BatchPublisherWithContext<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(null, batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and with the provided options
     * where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * @param name              the name to use
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     * @see #newPublisherDataLoaderWithTry(BatchPublisher)
     */
    public static <K, V> DataLoader<K, V> newPublisherDataLoaderWithTry(String name, BatchPublisherWithContext<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(nonNull(name), batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
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
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedPublisherDataLoader(MappedBatchPublisher<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(null, batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param name              the name to use
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedPublisherDataLoader(String name, MappedBatchPublisher<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(nonNull(name), batchLoadFunction, options);
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
     * @return a new DataLoader
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    public static <K, V> DataLoader<K, V> newMappedPublisherDataLoaderWithTry(MappedBatchPublisher<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(null, batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and with the provided options
     * where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * @param name              the name to use
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    public static <K, V> DataLoader<K, V> newMappedPublisherDataLoaderWithTry(String name, MappedBatchPublisher<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(nonNull(name), batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
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
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedPublisherDataLoader(MappedBatchPublisherWithContext<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(null, batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param name              the name to use
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newMappedPublisherDataLoader(String name, MappedBatchPublisherWithContext<K, V> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(nonNull(name), batchLoadFunction, options);
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
     * @return a new DataLoader
     * @see #newMappedPublisherDataLoaderWithTry(MappedBatchPublisher)
     */
    public static <K, V> DataLoader<K, V> newMappedPublisherDataLoaderWithTry(MappedBatchPublisherWithContext<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(null, batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and with the provided options
     * where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * @param name              the name to use
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     * @return a new DataLoader
     * @see #newMappedPublisherDataLoaderWithTry(MappedBatchPublisher)
     */
    public static <K, V> DataLoader<K, V> newMappedPublisherDataLoaderWithTry(String name, MappedBatchPublisherWithContext<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return mkDataLoader(nonNull(name), batchLoadFunction, options);
    }

    static <K, V> DataLoader<K, V> mkDataLoader(@Nullable String name, Object batchLoadFunction, @Nullable DataLoaderOptions options) {
        return new DataLoader<>(name, batchLoadFunction, options);
    }

    /**
     * Return a new {@link Builder} of a data loader.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @return a new {@link Builder} of a data loader
     */
    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    /**
     * Return a new {@link Builder} of a data loader using the specified one as a template.
     *
     * @param <K>        the key type
     * @param <V>        the value type
     * @param dataLoader the {@link DataLoader} to copy values from into the builder
     * @return a new {@link Builder} of a data loader
     */
    public static <K, V> Builder<K, V> builder(DataLoader<K, V> dataLoader) {
        return new Builder<>(dataLoader);
    }

    /**
     * A builder of {@link DataLoader}s
     *
     * @param <K> the key type
     * @param <V> the value type
     */
    public static class Builder<K, V> {
        String name;
        Object batchLoadFunction;
        DataLoaderOptions options = DataLoaderOptions.newDefaultOptions();

        Builder() {
        }

        Builder(DataLoader<?, ?> dataLoader) {
            this.name = dataLoader.getName();
            this.batchLoadFunction = dataLoader.getBatchLoadFunction();
            this.options = dataLoader.getOptions();
        }

        public Builder<K, V> name(String name) {
            this.name = name;
            return this;
        }

        public Builder<K, V> options(DataLoaderOptions options) {
            this.options = options;
            return this;
        }

        public Builder<K, V> batchLoadFunction(Object batchLoadFunction) {
            this.batchLoadFunction = batchLoadFunction;
            return this;
        }

        public Builder<K, V> batchLoader(BatchLoader<K, V> batchLoadFunction) {
            this.batchLoadFunction = batchLoadFunction;
            return this;
        }

        public Builder<K, V> batchLoader(BatchLoaderWithContext<K, V> batchLoadFunction) {
            this.batchLoadFunction = batchLoadFunction;
            return this;
        }

        public Builder<K, V> mappedBatchLoader(MappedBatchLoader<K, V> batchLoadFunction) {
            this.batchLoadFunction = batchLoadFunction;
            return this;
        }

        public Builder<K, V> mappedBatchLoader(MappedBatchLoaderWithContext<K, V> batchLoadFunction) {
            this.batchLoadFunction = batchLoadFunction;
            return this;
        }

        public Builder<K, V> publisherBatchLoader(BatchPublisher<K, V> batchLoadFunction) {
            this.batchLoadFunction = batchLoadFunction;
            return this;
        }

        public Builder<K, V> publisherBatchLoader(BatchPublisherWithContext<K, V> batchLoadFunction) {
            this.batchLoadFunction = batchLoadFunction;
            return this;
        }

        public Builder<K, V> mappedPublisherBatchLoader(MappedBatchPublisher<K, V> batchLoadFunction) {
            this.batchLoadFunction = batchLoadFunction;
            return this;
        }

        public Builder<K, V> mappedPublisherBatchLoader(MappedBatchPublisherWithContext<K, V> batchLoadFunction) {
            this.batchLoadFunction = batchLoadFunction;
            return this;
        }

        public DataLoader<K, V> build() {
            return mkDataLoader(name, batchLoadFunction, options);
        }
    }
}


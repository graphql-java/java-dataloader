package org.dataloader;

/**
 * A interface intended as a delegate for other Observer-like classes used in other libraries, to be invoked by the calling
 * {@link MappedObserverBatchLoader}.
 * <p>
 * Some examples include:
 * <ul>
 *     <li>Project Reactor's <a href="https://www.reactive-streams.org/reactive-streams-1.0.4-javadoc/org/reactivestreams/Subscriber.html">{@code Subscriber}</a>
 *     <li>gRPC's <a href="https://grpc.github.io/grpc-java/javadoc/io/grpc/stub/StreamObserver.html">{@code StreamObserver}</a>
 *     <li>RX Java's <a href="https://reactivex.io/RxJava/3.x/javadoc/io/reactivex/rxjava3/core/Flowable.html">{@code Flowable}</a>
 * </ul>
 * @param <K> the key type of the calling {@link MappedObserverBatchLoader}.
 * @param <V> the value type of the calling {@link MappedObserverBatchLoader}.
 */
public interface MappedBatchObserver<K, V> {

    /**
     * To be called by the {@link MappedObserverBatchLoader} to process a new key/value pair.
     */
    void onNext(K key, V value);

    /**
     * To be called by the {@link MappedObserverBatchLoader} to indicate all values have been successfully processed.
     * This {@link MappedBatchObserver} should not have any method invoked after this method is called.
     */
    void onCompleted();

    /**
     * To be called by the {@link MappedObserverBatchLoader} to indicate an unrecoverable error has been encountered.
     * This {@link MappedBatchObserver} should not have any method invoked after this method is called.
     */
    void onError(Throwable e);
}

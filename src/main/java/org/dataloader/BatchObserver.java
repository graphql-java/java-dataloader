package org.dataloader;

/**
 * A interface intended as a delegate for other Observer-like classes used in other libraries, to be invoked by the calling
 * {@link ObserverBatchLoader}.
 * <p>
 * Some examples include:
 * <ul>
 *     <li>Project Reactor's <a href="https://www.reactive-streams.org/reactive-streams-1.0.4-javadoc/org/reactivestreams/Subscriber.html">{@code Subscriber}</a>
 *     <li>gRPC's <a href="https://grpc.github.io/grpc-java/javadoc/io/grpc/stub/StreamObserver.html">{@code StreamObserver}</a>
 *     <li>RX Java's <a href="https://reactivex.io/RxJava/3.x/javadoc/io/reactivex/rxjava3/core/Flowable.html">{@code Flowable}</a>
 * </ul>
 * @param <V> the value type of the {@link ObserverBatchLoader}
 */
public interface BatchObserver<V> {

    /**
     * To be called by the {@link ObserverBatchLoader} to load a new value.
     */
    void onNext(V value);

    /**
     * To be called by the {@link ObserverBatchLoader} to indicate all values have been successfully processed.
     * This {@link BatchObserver} should not have any method invoked after this is called.
     */
    void onCompleted();

    /**
     * To be called by the {@link ObserverBatchLoader} to indicate an unrecoverable error has been encountered.
     * This {@link BatchObserver} should not have any method invoked after this is called.
     */
    void onError(Throwable e);
}

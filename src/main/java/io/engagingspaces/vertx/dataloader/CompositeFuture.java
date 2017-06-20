package io.engagingspaces.vertx.dataloader;

import java.util.List;
import java.util.function.Consumer;

/**
 * TODO: Document this class / interface here
 *
 * @since v0.x
 */
public class CompositeFuture {

    public CompositeFuture setHandler(Consumer<CompositeFuture> handler) {
        return this;
    }

    public boolean succeeded() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Throwable cause() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public boolean succeeded(int index) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Throwable cause(int index) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public CompositeFuture result() {
        return this;
    }

    public <V> V resultAt(int index) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public static <T> CompositeFuture join(List<T> objects) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public <T> T list() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public <T> T size() {
        throw new UnsupportedOperationException("Not implemented");
    }

    public boolean isComplete() {
        throw new UnsupportedOperationException("Not implemented");
    }
}

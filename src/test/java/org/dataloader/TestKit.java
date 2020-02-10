package org.dataloader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.dataloader.impl.CompletableFutureKit.failedFuture;

public class TestKit {

    public static Collection<Integer> listFrom(int i, int max) {
        List<Integer> ints = new ArrayList<>();
        for (int j = i; j < max; j++) {
            ints.add(j);
        }
        return ints;
    }

    public static <V> CompletableFuture<V> futureError() {
        return failedFuture(new IllegalStateException("Error"));
    }
}

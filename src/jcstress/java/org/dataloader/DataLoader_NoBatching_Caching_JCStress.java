package org.dataloader;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE_INTERESTING;

@JCStressTest
@State
@Outcome(id = "1000, 1000", expect = ACCEPTABLE, desc = "No keys loaded twice")
@Outcome(id = "1.*, 1000", expect = ACCEPTABLE_INTERESTING, desc = "Some keys loaded twice")
public class DataLoader_NoBatching_Caching_JCStress {


    AtomicInteger batchLoaderCount = new AtomicInteger();

    BatchLoader<String, String> batchLoader = keys -> {
        batchLoaderCount.getAndAdd(keys.size());
        return CompletableFuture.completedFuture(keys);
    };


    DataLoader<String, String> dataLoader = DataLoaderFactory.newDataLoader(batchLoader, DataLoaderOptions.newOptions().setBatchingEnabled(false).build());

    @Actor
    public void load1() {
        for (int i = 0; i < 1000; i++) {
            dataLoader.load("load-1-" + i);
        }
    }

    @Actor
    public void load2() {
        for (int i = 0; i < 1000; i++) {
            dataLoader.load("load-1-" + i);
        }
    }


    @Arbiter
    public void arbiter(II_Result r) {
        r.r1 = batchLoaderCount.get();
        r.r2 = dataLoader.getCacheMap().size();
    }

}

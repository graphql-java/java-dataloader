package org.dataloader;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;

@JCStressTest
@State
@Outcome(id = "2000, 2000", expect = ACCEPTABLE, desc = "accepted")
public class DataLoaderDispatchJCStress {


    AtomicInteger counter = new AtomicInteger();
    AtomicInteger batchLoaderCount = new AtomicInteger();
    volatile boolean finished1;
    volatile boolean finished2;


    BatchLoader<String, String> batchLoader = keys -> {
        return CompletableFuture.supplyAsync(() -> {
            batchLoaderCount.getAndAdd(keys.size());
            return keys;
        });
    };
    DataLoader<String, String> dataLoader = DataLoaderFactory.newDataLoader(batchLoader);

    public DataLoaderDispatchJCStress() {

    }

    @Actor
    public void load1() {
        for (int i = 0; i < 1000; i++) {
            dataLoader.load("load-1-" + i);
        }
        finished1 = true;
    }

    @Actor
    public void load2() {
        for (int i = 0; i < 1000; i++) {
            dataLoader.load("load-2-" + i);
        }
        finished2 = true;
    }


    @Actor
    public void dispatch1() {
        while (!finished1 || !finished2) {
            try {
                List<String> dispatchedResult = dataLoader.dispatch().get();
                counter.getAndAdd(dispatchedResult.size());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        try {
            List<String> dispatchedResult = dataLoader.dispatch().get();
            counter.getAndAdd(dispatchedResult.size());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Actor
    public void dispatch2() {
        while (!finished1 || !finished2) {
            try {
                List<String> dispatchedResult = dataLoader.dispatch().get();
                counter.getAndAdd(dispatchedResult.size());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        try {
            List<String> dispatchedResult = dataLoader.dispatch().get();
            counter.getAndAdd(dispatchedResult.size());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Arbiter
    public void arbiter(II_Result r) {
        r.r1 = counter.get();
        r.r2 = batchLoaderCount.get();
    }


}

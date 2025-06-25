package org.dataloader

import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import java.util.concurrent.CompletableFuture.completedFuture

/**
 * Some Kotlin code to prove that are JSpecify annotations work here
 * as expected in Kotlin land.  We don't intend to ue Kotlin in our tests
 * or to deliver Kotlin code in the java
 */
class KotlinExamples {

    @Test
    fun `basic kotlin test of non nullable value types`() {
        val batchLoadFunction = BatchLoader<String, String>
        { keys -> completedFuture(keys.toList()) }
        val dataLoader: DataLoader<String, String> =
            DataLoaderFactory.newDataLoader(batchLoadFunction)

        val cfA = dataLoader.load("A")
        val cfB = dataLoader.load("B")

        dataLoader.dispatch()

        assert(cfA.join().equals("A"))
        assert(cfB.join().equals("B"))
    }

    @Test
    fun `basic kotlin test of nullable value types`() {
        val batchLoadFunction: BatchLoader<String, String?> = BatchLoader { keys -> completedFuture(keys.toList()) }
        val dataLoader: DataLoader<String, String?> = DataLoaderFactory.newDataLoader(batchLoadFunction)

        standardNullableAsserts(dataLoader)
    }

    @Test
    fun `basic kotlin test of nullable value types in mapped batch loader`() {
        val batchLoadFunction = MappedBatchLoader<String, String?>
        { keys -> completedFuture(keys.associateBy({ it })) }

        val dataLoader: DataLoader<String, String?> = DataLoaderFactory.newMappedDataLoader(batchLoadFunction)

        standardNullableAsserts(dataLoader)
    }

    @Test
    fun `basic kotlin test of nullable value types in mapped batch loader with context`() {
        val batchLoadFunction = MappedBatchLoaderWithContext<String, String?>
        { keys, env -> completedFuture(keys.associateBy({ it })) }

        val dataLoader: DataLoader<String, String?> = DataLoaderFactory.newMappedDataLoader(batchLoadFunction)

        standardNullableAsserts(dataLoader)
    }

    @Test
    fun `basic kotlin test of nullable value types in mapped batch publisher`() {
        val batchLoadFunction = MappedBatchPublisher<String, String?>
        { keys, subscriber ->
            val map: Map<String, String?> = keys.associateBy({ it })
            Flux.fromIterable(map.entries).subscribe(subscriber);
        }

        val dataLoader: DataLoader<String, String?> = DataLoaderFactory.newMappedPublisherDataLoader(batchLoadFunction)

        standardNullableAsserts(dataLoader)
    }

    @Test
    fun `basic kotlin test of nullable value types in mapped batch publisher with context`() {
        val batchLoadFunction = MappedBatchPublisherWithContext<String, String?>
        { keys, subscriber, env ->
            val map: Map<String, String?> = keys.associateBy({ it })
            Flux.fromIterable(map.entries).subscribe(subscriber);
        }

        val dataLoader: DataLoader<String, String?> = DataLoaderFactory.newMappedPublisherDataLoader(batchLoadFunction)

        standardNullableAsserts(dataLoader)
    }

    private fun standardNullableAsserts(dataLoader: DataLoader<String, String?>) {
        val cfA = dataLoader.load("A")
        val cfB = dataLoader.load("B")

        dataLoader.dispatch()

        assert(cfA.join().equals("A"))
        assert(cfB.join().equals("B"))
    }


}
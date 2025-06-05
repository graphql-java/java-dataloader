package org.dataloader

import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.*

/**
 * Some Kotlin code to prove that are JSpecify annotations work here
 * as expected in Kotlin land.  We don't intend to ue Kotlin in our tests
 * or to deliver Kotlin code in the java
 */
class KotlinExamples {

    @Test
    fun `basic kotlin test of non nullable value types`() {
        val dataLoader: DataLoader<String, String> = DataLoaderFactory.newDataLoader { keys -> completedFuture(keys.toList()) }

        val cfA = dataLoader.load("A")
        val cfB = dataLoader.load("B")

        dataLoader.dispatch()

        assert(cfA.join().equals("A"))
        assert(cfA.join().equals("A"))
    }

    @Test
    fun `basic kotlin test of nullable value types`() {
        val dataLoader: DataLoader<String, String?> = DataLoaderFactory.newDataLoader { keys -> completedFuture(keys.toList()) }

        val cfA = dataLoader.load("A")
        val cfB = dataLoader.load("B")

        dataLoader.dispatch()

        assert(cfA.join().equals("A"))
        assert(cfA.join().equals("A"))
    }

}
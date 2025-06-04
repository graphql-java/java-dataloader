package org.dataloader

import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

/**
 * Some Kotlin code to prove that are JSpecify annotations work here
 * as expected in Kotlin land.  We don't intend to ue Kotlin in our tests
 * or to deliver Kotlin code in the java
 */
class KotlinExamples {

    @Test
    fun `basic kotlin test of non nullable value types`() {
        val dataLoader: DataLoader<String, String> = DataLoaderFactory.newDataLoader { keys -> CompletableFuture.completedFuture(keys.toList()) }

        val cfA = dataLoader.load("A")
        val cfB = dataLoader.load("B")

        dataLoader.dispatch()

        cfA.join().equals("A")
        cfB.join().equals("B")
    }

    @Test
    fun `basic kotlin test of nullable value types`() {
        val dataLoader: DataLoader<String, String?> = DataLoaderFactory.newDataLoader { keys -> CompletableFuture.completedFuture(keys.toList()) }

        val cfA = dataLoader.load("A")
        val cfB = dataLoader.load("B")

        dataLoader.dispatch()

        cfA.join().equals("A")
        cfB.join().equals("B")
    }

}
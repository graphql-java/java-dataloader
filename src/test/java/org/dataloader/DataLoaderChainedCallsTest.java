package org.dataloader;

import org.dataloader.fixtures.User;
import org.dataloader.fixtures.UserManager;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.awaitility.Awaitility.await;
import static org.dataloader.DataLoaderFactory.newDataLoader;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class DataLoaderChainedCallsTest {


    @Test
    public void should_allow_composition_of_data_loader_calls_to_each_other() {
        UserManager userManager = new UserManager();

        BatchLoader<Long, User> userBatchLoader = userIds -> CompletableFuture
                .supplyAsync(() -> {
                    Stream<User> userStream = userIds
                            .stream()
                            .map(userManager::loadUserById);
                    return userStream
                            .collect(Collectors.toList());
                });
        DataLoader<Long, User> userLoader = newDataLoader(userBatchLoader);

        // this will under batch - that is the second `.dispatch()` calls will
        // cause single values to be thrown at the batch function

        AtomicBoolean gandalfCalled = new AtomicBoolean(false);
        AtomicBoolean sarumanCalled = new AtomicBoolean(false);

        userLoader.load(1L)
                .thenCompose(user -> {
                    CompletableFuture<User> cf = userLoader.load(user.getInvitedByID());
                    // without this extra dispatch() call - this wont complete
                    userLoader.dispatch();
                    return cf;
                })
                .thenAccept(invitedBy -> {
                    gandalfCalled.set(true);
                    assertThat(invitedBy.getName(), equalTo("Manwë"));
                })
        ;

        userLoader.load(2L)
                .thenCompose(user -> {
                    CompletableFuture<User> cf = userLoader.load(user.getInvitedByID());
                    // without this extra dispatch() call - this wont complete
                    userLoader.dispatch();
                    return cf;
                })
                .thenAccept(invitedBy -> {
                    sarumanCalled.set(true);
                    assertThat(invitedBy.getName(), equalTo("Aulë"));
                    userLoader.dispatch();
                });

        userLoader.dispatch();

        await().untilTrue(gandalfCalled);
        await().untilTrue(sarumanCalled);
    }
}

import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.fixtures.User;
import org.dataloader.fixtures.UserManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public class ReadmeExamples {

    UserManager userManager = new UserManager();

    public static void main(String[] args) {
        ReadmeExamples examples = new ReadmeExamples();
        examples.basicExample();
    }

    @SuppressWarnings({"Convert2Lambda", "Convert2MethodRef", "CodeBlock2Expr"})
    void basicExample() {

        BatchLoader<Long, User> lessEfficientUserBatchLoader = new BatchLoader<Long, User>() {
            @Override
            public CompletionStage<List<User>> load(List<Long> userIds) {
                return CompletableFuture.supplyAsync(() -> {
                    //
                    // notice how it makes N calls to load by single user id out of the batch of N keys
                    //
                    return userIds.stream()
                            .map(id -> userManager.loadUserById(id))
                            .collect(Collectors.toList());
                });
            }
        };

        BatchLoader<Long, User> userBatchLoader = new BatchLoader<Long, User>() {
            @Override
            public CompletionStage<List<User>> load(List<Long> userIds) {
                return CompletableFuture.supplyAsync(() -> {
                    return userManager.loadUsersById(userIds);
                });
            }
        };

        DataLoader<Long, User> userLoader = new DataLoader<>(userBatchLoader);

        CompletionStage<User> load1 = userLoader.load(1L);

        userLoader.load(1L)
                .thenAccept(user -> {
                    System.out.println("user = " + user);
                    userLoader.load(user.getInvitedByID())
                            .thenAccept(invitedBy -> {
                                System.out.println("invitedBy = " + invitedBy);
                            });
                });

        userLoader.load(2L)
                .thenAccept(user -> {
                    System.out.println("user = " + user);
                    userLoader.load(user.getInvitedByID())
                            .thenAccept(invitedBy -> {
                                System.out.println("invitedBy = " + invitedBy);
                            });
                });

        userLoader.dispatchAndJoin();
    }

}

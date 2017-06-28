import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.PromisedValues;
import org.dataloader.fixtures.User;
import org.dataloader.fixtures.UserManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ReadmeExamples {

    UserManager userManager = new UserManager();

    public static void main(String[] args) {
        ReadmeExamples examples = new ReadmeExamples();
        examples.basicExample();
    }

    @SuppressWarnings({"Convert2Lambda", "Convert2MethodRef", "CodeBlock2Expr"})
    void basicExample() {

        BatchLoader<Long, User> userBatchLoader = new BatchLoader<Long, User>() {
            @Override
            public PromisedValues<User> load(List<Long> userIds) {
                List<CompletableFuture<User>> futures = userIds.stream()
                        .map(userId ->
                                CompletableFuture.supplyAsync(() ->
                                        userManager.loadUsersById(userId)))
                        .collect(Collectors.toList());
                return PromisedValues.allOf(futures);
            }
        };
        DataLoader<Long, User> userLoader = new DataLoader<>(userBatchLoader);

        CompletableFuture<User> load1 = userLoader.load(1L);

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

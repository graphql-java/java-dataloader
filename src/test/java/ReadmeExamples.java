import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.PromisedValues;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        userLoader.dispatch().join();
        userLoader.dispatch().join();
    }

    class User {
        String name;
        Long invitedByID;

        public User(Long invitedByID, String name) {
            this.name = name;
            this.invitedByID = invitedByID;
        }

        public String getName() {
            return name;
        }

        public Long getInvitedByID() {
            return invitedByID;
        }

        @Override
        public String toString() {
            return "User{" +
                    "name='" + name + '\'' +
                    "invitedByID='" + invitedByID + '\'' +
                    '}';
        }
    }

    class UserManager {

        Map<Long, User> users = new LinkedHashMap<>();

        {
            users.put(10001L, new User(-1L, "Aulë"));
            users.put(10002L, new User(-1L, "Oromë"));
            users.put(10003L, new User(-1L, "Yavanna"));
            users.put(10004L, new User(-1L, "Manwë"));

            users.put(1L, new User(10004L, "Olórin"));
            users.put(2L, new User(10001L, "Curunir"));
            users.put(3L, new User(10002L, "Alatar"));
            users.put(4L, new User(10003L, "Aiwendil"));
        }

        public User loadUsersById(Long userId) {
            return users.get(userId);
        }
    }
}

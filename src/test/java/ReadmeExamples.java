import org.dataloader.BatchLoader;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.CacheMap;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;
import org.dataloader.MapBatchLoader;
import org.dataloader.Try;
import org.dataloader.fixtures.SecurityCtx;
import org.dataloader.fixtures.User;
import org.dataloader.fixtures.UserManager;
import org.dataloader.stats.Statistics;
import org.dataloader.stats.ThreadLocalStatisticsCollector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static java.lang.String.format;

@SuppressWarnings("ALL")
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

    private void callContextExample() {
        BatchLoader<String, String> batchLoader = new BatchLoader<String, String>() {
            //
            // the reason this method exists is for backwards compatibility.  There are a large
            // number of existing dataloader clients out there that used this method before the call context was invented
            // and hence to preserve compatibility we have this unfortunate method declaration.
            //
            @Override
            public CompletionStage<List<String>> load(List<String> keys) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletionStage<List<String>> load(List<String> keys, BatchLoaderEnvironment environment) {
                SecurityCtx callCtx = environment.getContext();
                return callDatabaseForResults(callCtx, keys);
            }
        };

        BatchLoaderEnvironment batchLoaderEnvironment = BatchLoaderEnvironment.newBatchLoaderEnvironment()
                .context(SecurityCtx.getCallingUserCtx()).build();

        DataLoaderOptions options = DataLoaderOptions.newOptions()
                .setBatchLoaderEnvironmentProvider(() -> batchLoaderEnvironment);
        DataLoader<String, String> loader = new DataLoader<>(batchLoader, options);
    }

    private CompletionStage<List<String>> callDatabaseForResults(SecurityCtx callCtx, List<String> keys) {
        return null;
    }

    private void mapBatchLoader() {
        MapBatchLoader<Long, User> mapBatchLoader = new MapBatchLoader<Long, User>() {
            @Override
            public CompletionStage<Map<Long, User>> load(List<Long> userIds, BatchLoaderEnvironment environment) {
                SecurityCtx callCtx = environment.getContext();
                return CompletableFuture.supplyAsync(() -> userManager.loadMapOfUsersById(callCtx, userIds));
            }
        };

        DataLoader<Long, User> userLoader = DataLoader.newDataLoader(mapBatchLoader);

        // ...
    }


    private void tryExample() {
        Try<String> tryS = Try.tryCall(() -> {
            if (rollDice()) {
                return "OK";
            } else {
                throw new RuntimeException("Bang");
            }
        });

        if (tryS.isSuccess()) {
            System.out.println("It work " + tryS.get());
        } else {
            System.out.println("It failed with exception :  " + tryS.getThrowable());

        }
    }

    private void tryBatcLoader() {
        DataLoader<String, User> dataLoader = DataLoader.newDataLoaderWithTry(new BatchLoader<String, Try<User>>() {
            @Override
            public CompletionStage<List<Try<User>>> load(List<String> keys) {
                return CompletableFuture.supplyAsync(() -> {
                    List<Try<User>> users = new ArrayList<>();
                    for (String key : keys) {
                        Try<User> userTry = loadUser(key);
                        users.add(userTry);
                    }
                    return users;
                });
            }
        });
    }

    DataLoader<String, User> userDataLoader;

    private void clearCacheOnError() {

        userDataLoader.load("r2d2").whenComplete((user, throwable) -> {
            if (throwable != null) {
                userDataLoader.clear("r2dr");
                throwable.printStackTrace();
            } else {
                processUser(user);
            }
        });
    }

    BatchLoader<String, User> userBatchLoader;

    private void disableCache() {
        new DataLoader<String, User>(userBatchLoader, DataLoaderOptions.newOptions().setCachingEnabled(false));


        userDataLoader.load("A");
        userDataLoader.load("B");
        userDataLoader.load("A");

        userDataLoader.dispatch();

        // will result in keys to the batch loader with [ "A", "B", "A" ]
    }

    class MyCustomCache implements CacheMap {
        @Override
        public boolean containsKey(Object key) {
            return false;
        }

        @Override
        public Object get(Object key) {
            return null;
        }

        @Override
        public CacheMap set(Object key, Object value) {
            return null;
        }

        @Override
        public CacheMap delete(Object key) {
            return null;
        }

        @Override
        public CacheMap clear() {
            return null;
        }
    }

    private void customCache() {

        MyCustomCache customCache = new MyCustomCache();
        DataLoaderOptions options = DataLoaderOptions.newOptions().setCacheMap(customCache);
        new DataLoader<String, User>(userBatchLoader, options);
    }

    private void processUser(User user) {

    }

    private Try<User> loadUser(String key) {
        return null;
    }

    private boolean rollDice() {
        return false;
    }


    private void statsExample() {
        Statistics statistics = userDataLoader.getStatistics();

        System.out.println(format("load : %d", statistics.getLoadCount()));
        System.out.println(format("batch load: %d", statistics.getBatchLoadCount()));
        System.out.println(format("cache hit: %d", statistics.getCacheHitCount()));
        System.out.println(format("cache hit ratio: %d", statistics.getCacheHitRatio()));
    }

    private void statsConfigExample() {

        DataLoaderOptions options = DataLoaderOptions.newOptions().setStatisticsCollector(() -> new ThreadLocalStatisticsCollector());
        DataLoader<String, User> userDataLoader = DataLoader.newDataLoader(userBatchLoader, options);
    }

}

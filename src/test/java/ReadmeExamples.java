import org.dataloader.BatchLoader;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.BatchLoaderWithContext;
import org.dataloader.BatchPublisher;
import org.dataloader.CacheMap;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.DispatchResult;
import org.dataloader.MappedBatchLoaderWithContext;
import org.dataloader.MappedBatchPublisher;
import org.dataloader.Try;
import org.dataloader.fixtures.SecurityCtx;
import org.dataloader.fixtures.User;
import org.dataloader.fixtures.UserManager;
import org.dataloader.instrumentation.DataLoaderInstrumentation;
import org.dataloader.instrumentation.DataLoaderInstrumentationContext;
import org.dataloader.instrumentation.DataLoaderInstrumentationHelper;
import org.dataloader.registries.DispatchPredicate;
import org.dataloader.registries.ScheduledDataLoaderRegistry;
import org.dataloader.scheduler.BatchLoaderScheduler;
import org.dataloader.stats.Statistics;
import org.dataloader.stats.ThreadLocalStatisticsCollector;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
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

        DataLoader<Long, User> userLoader = DataLoaderFactory.newDataLoader(userBatchLoader);

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
        DataLoaderOptions options = DataLoaderOptions.newOptions()
                .setBatchLoaderContextProvider(() -> SecurityCtx.getCallingUserCtx()).build();

        BatchLoaderWithContext<String, String> batchLoader = new BatchLoaderWithContext<String, String>() {
            @Override
            public CompletionStage<List<String>> load(List<String> keys, BatchLoaderEnvironment environment) {
                SecurityCtx callCtx = environment.getContext();
                return callDatabaseForResults(callCtx, keys);
            }
        };

        DataLoader<String, String> loader = DataLoaderFactory.newDataLoader(batchLoader, options);
    }

    private void keyContextExample() {
        DataLoaderOptions options = DataLoaderOptions.newOptions()
                .setBatchLoaderContextProvider(() -> SecurityCtx.getCallingUserCtx()).build();

        BatchLoaderWithContext<String, String> batchLoader = new BatchLoaderWithContext<String, String>() {
            @Override
            public CompletionStage<List<String>> load(List<String> keys, BatchLoaderEnvironment environment) {
                SecurityCtx callCtx = environment.getContext();
                //
                // this is the load context objects in map form by key
                // in this case [ keyA : contextForA, keyB : contextForB ]
                //
                Map<Object, Object> keyContexts = environment.getKeyContexts();
                //
                // this is load context in list form
                //
                // in this case [ contextForA, contextForB ]
                return callDatabaseForResults(callCtx, keys);
            }
        };

        DataLoader<String, String> loader = DataLoaderFactory.newDataLoader(batchLoader, options);
        loader.load("keyA", "contextForA");
        loader.load("keyB", "contextForB");
    }

    private CompletionStage<List<String>> callDatabaseForResults(SecurityCtx callCtx, List<String> keys) {
        return null;
    }

    private void mapBatchLoader() {
        MappedBatchLoaderWithContext<Long, User> mapBatchLoader = new MappedBatchLoaderWithContext<Long, User>() {
            @Override
            public CompletionStage<Map<Long, User>> load(Set<Long> userIds, BatchLoaderEnvironment environment) {
                SecurityCtx callCtx = environment.getContext();
                return CompletableFuture.supplyAsync(() -> userManager.loadMapOfUsersByIds(callCtx, userIds));
            }
        };

        DataLoader<Long, User> userLoader = DataLoaderFactory.newMappedDataLoader(mapBatchLoader);

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

    private void tryBatchLoader() {
        DataLoader<String, User> dataLoader = DataLoaderFactory.newDataLoaderWithTry(new BatchLoader<String, Try<User>>() {
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

    private void batchPublisher() {
        BatchPublisher<Long, User> batchPublisher = new BatchPublisher<Long, User>() {
            @Override
            public void load(List<Long> userIds, Subscriber<User> userSubscriber) {
                Publisher<User> userResults = userManager.streamUsersById(userIds);
                userResults.subscribe(userSubscriber);
            }
        };
        DataLoader<Long, User> userLoader = DataLoaderFactory.newPublisherDataLoader(batchPublisher);
    }

    private void mappedBatchPublisher() {
        MappedBatchPublisher<Long, User> mappedBatchPublisher = new MappedBatchPublisher<Long, User>() {
            @Override
            public void load(Set<Long> userIds, Subscriber<Map.Entry<Long, User>> userEntrySubscriber) {
                Publisher<Map.Entry<Long, User>> userEntries = userManager.streamUsersById(userIds);
                userEntries.subscribe(userEntrySubscriber);
            }
        };
        DataLoader<Long, User> userLoader = DataLoaderFactory.newMappedPublisherDataLoader(mappedBatchPublisher);
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
    BatchLoader<String, User> teamsBatchLoader;

    private void disableCache() {
        DataLoaderFactory.newDataLoader(userBatchLoader, DataLoaderOptions.newOptions().setCachingEnabled(false).build());


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
        public CompletableFuture<Object> get(Object key) {
            return null;
        }

        @Override
        public Collection<CompletableFuture<Object>> getAll() {
            return null;
        }

        @Override
        public CompletableFuture<Object> setIfAbsent(Object key, CompletableFuture value) {
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
        DataLoaderOptions options = DataLoaderOptions.newOptions().setCacheMap(customCache).build();
        DataLoaderFactory.newDataLoader(userBatchLoader, options);
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

        DataLoaderOptions options = DataLoaderOptions.newOptions().setStatisticsCollector(() -> new ThreadLocalStatisticsCollector()).build();
        DataLoader<String, User> userDataLoader = DataLoaderFactory.newDataLoader(userBatchLoader, options);
    }

    private void snooze(int i) {
    }

    private void BatchLoaderSchedulerExample() {
        new BatchLoaderScheduler() {

            @Override
            public <K, V> CompletionStage<List<V>> scheduleBatchLoader(ScheduledBatchLoaderCall<V> scheduledCall, List<K> keys, BatchLoaderEnvironment environment) {
                return CompletableFuture.supplyAsync(() -> {
                    snooze(10);
                    return scheduledCall.invoke();
                }).thenCompose(Function.identity());
            }

            @Override
            public <K, V> CompletionStage<Map<K, V>> scheduleMappedBatchLoader(ScheduledMappedBatchLoaderCall<K, V> scheduledCall, List<K> keys, BatchLoaderEnvironment environment) {
                return CompletableFuture.supplyAsync(() -> {
                    snooze(10);
                    return scheduledCall.invoke();
                }).thenCompose(Function.identity());
            }

            @Override
            public <K> void scheduleBatchPublisher(ScheduledBatchPublisherCall scheduledCall, List<K> keys, BatchLoaderEnvironment environment) {
                snooze(10);
                scheduledCall.invoke();
            }
        };
    }

    private void ScheduledDispatcher() {
        DispatchPredicate depthOrTimePredicate = DispatchPredicate.dispatchIfDepthGreaterThan(10)
                .or(DispatchPredicate.dispatchIfLongerThan(Duration.ofMillis(200)));

        ScheduledDataLoaderRegistry registry = ScheduledDataLoaderRegistry.newScheduledRegistry()
                .dispatchPredicate(depthOrTimePredicate)
                .schedule(Duration.ofMillis(10))
                .register("users", userDataLoader)
                .build();
    }


    DataLoader<String, User> dataLoaderA = DataLoaderFactory.newDataLoader(userBatchLoader);
    DataLoader<User, Object> dataLoaderB = DataLoaderFactory.newDataLoader(keys -> {
        return CompletableFuture.completedFuture(Collections.singletonList(1L));
    });

    private void ScheduledDispatcherChained() {
        CompletableFuture<Object> chainedCalls = dataLoaderA.load("user1")
                .thenCompose(userAsKey -> dataLoaderB.load(userAsKey));


        CompletableFuture<Object> chainedWithImmediateDispatch = dataLoaderA.load("user1")
                .thenCompose(userAsKey -> {
                    CompletableFuture<Object> loadB = dataLoaderB.load(userAsKey);
                    dataLoaderB.dispatch();
                    return loadB;
                });


        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

        ScheduledDataLoaderRegistry registry = ScheduledDataLoaderRegistry.newScheduledRegistry()
                .register("a", dataLoaderA)
                .register("b", dataLoaderB)
                .scheduledExecutorService(executorService)
                .schedule(Duration.ofMillis(10))
                .tickerMode(true) // ticker mode is on
                .build();

    }

    private DataLoaderInstrumentation timingInstrumentation = DataLoaderInstrumentationHelper.NOOP_INSTRUMENTATION;

    private void instrumentationExample() {

        DataLoaderInstrumentation timingInstrumentation = new DataLoaderInstrumentation() {
            @Override
            public DataLoaderInstrumentationContext<DispatchResult<?>> beginDispatch(DataLoader<?, ?> dataLoader) {
                long then = System.currentTimeMillis();
                return DataLoaderInstrumentationHelper.whenCompleted((result, err) -> {
                    long ms = System.currentTimeMillis() - then;
                    System.out.println(format("dispatch time: %d ms", ms));
                });
            }

            @Override
            public DataLoaderInstrumentationContext<List<?>> beginBatchLoader(DataLoader<?, ?> dataLoader, List<?> keys, BatchLoaderEnvironment environment) {
                long then = System.currentTimeMillis();
                return DataLoaderInstrumentationHelper.whenCompleted((result, err) -> {
                    long ms = System.currentTimeMillis() - then;
                    System.out.println(format("batch loader time: %d ms", ms));
                });
            }
        };
        DataLoaderOptions options = DataLoaderOptions.newOptions().setInstrumentation(timingInstrumentation).build();
        DataLoader<String, User> userDataLoader = DataLoaderFactory.newDataLoader(userBatchLoader, options);
    }

    private void registryExample() {
        DataLoader<String, User> userDataLoader = DataLoaderFactory.newDataLoader(userBatchLoader);
        DataLoader<String, User> teamsDataLoader = DataLoaderFactory.newDataLoader(teamsBatchLoader);

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .instrumentation(timingInstrumentation)
                .register("users", userDataLoader)
                .register("teams", teamsDataLoader)
                .build();

        DataLoader<String, User> changedUsersDataLoader = registry.getDataLoader("users");

    }

    private void combiningRegistryExample() {
        DataLoader<String, User> userDataLoader = DataLoaderFactory.newDataLoader(userBatchLoader);
        DataLoader<String, User> teamsDataLoader = DataLoaderFactory.newDataLoader(teamsBatchLoader);

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .register("users", userDataLoader)
                .register("teams", teamsDataLoader)
                .build();

        DataLoaderRegistry registryCombined = DataLoaderRegistry.newRegistry()
                .instrumentation(timingInstrumentation)
                .registerAll(registry)
                .build();

        DataLoader<String, User> changedUsersDataLoader = registryCombined.getDataLoader("users");

    }
}

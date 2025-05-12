# java-dataloader

[![Build](https://github.com/graphql-java/java-dataloader/actions/workflows/master.yml/badge.svg)](https://github.com/graphql-java/java-dataloader/actions/workflows/master.yml)
[![Latest Release](https://img.shields.io/maven-central/v/com.graphql-java/java-dataloader?versionPrefix=4.)](https://maven-badges.herokuapp.com/maven-central/com.graphql-java/graphql-java/)
[![Latest Snapshot](https://img.shields.io/maven-central/v/com.graphql-java/java-dataloader?label=maven-central%20snapshot&versionPrefix=0)](https://maven-badges.herokuapp.com/maven-central/com.graphql-java/graphql-java/)
[![Apache licensed](https://img.shields.io/hexpm/l/plug.svg?maxAge=2592000)](https://github.com/graphql-java/java-dataloader/blob/master/LICENSE)

This small and simple utility library is a pure Java 11 port of [Facebook DataLoader](https://github.com/facebook/dataloader). 

It can serve as integral part of your application's data layer to provide a
consistent API over various back-ends and reduce message communication overhead through batching and caching.

An important use case for `java-dataloader` is improving the efficiency of GraphQL query execution.  Graphql fields
are resolved independently and, with a true graph of objects, you may be fetching the same object many times.  

A naive implementation of graphql data fetchers can easily lead to the dreaded  "n+1" fetch problem. 

Most of the code is ported directly from Facebook's reference implementation, with one IMPORTANT adaptation to make
it work for Java 11. ([more on this below](#manual-dispatching)).

Before reading on, be sure to take a short dive into the
[original documentation](https://github.com/facebook/dataloader/blob/master/README.md) provided by Lee Byron (@leebyron)
and Nicholas Schrock (@schrockn) from [Facebook](https://www.facebook.com/), the creators of the original data loader.

## Table of contents

- [Features](#features)
- [Getting started!](#getting-started)
  - [Installing](#installing)
  - [Building](#building)
- [Examples](#examples)
- [Other information sources](#other-information-sources)
- [Contributing](#contributing)
- [Acknowledgements](#acknowledgements)
- [Licensing](#licensing)

## Features

`java-dataloader` is a feature-complete port of the Facebook reference implementation with [one major difference](#manual-dispatching). These features are:

- Simple, intuitive API, using generics and fluent coding
- Define batch load function with lambda expression
- Schedule a load request in queue for batching
- Add load requests from anywhere in code
- Request returns a [`CompleteableFuture<V>`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html) of the requested value
- Can create multiple requests at once
- Caches load requests, so data is only fetched once
- Can clear individual cache keys, so data is re-fetched on next batch queue dispatch
- Can prime the cache with key/values, to avoid data being fetched needlessly
- Can configure cache key function with lambda expression to extract cache key from complex data loader key types
- Individual batch futures complete / resolve as batch is processed
- Results are ordered according to insertion order of load requests
- Deals with partial errors when a batch future fails
- Can disable batching and/or caching in configuration
- Can supply your own `CacheMap<K, V>` implementations
- Can supply your own `ValueCache<K, V>` implementations
- Has very high test coverage 

## Getting started!

### Installing

Gradle users configure the `java-dataloader` dependency in `build.gradle`:

```
repositories {
    mavenCentral()
}

dependencies {
    compile 'com.graphql-java:java-dataloader: 4.0.0'
}
```

### Building

To build from source use the Gradle wrapper:

```
./gradlew clean build
```


## Examples

A `DataLoader` object requires a `BatchLoader` function that is responsible for loading a promise of values given
a list of keys

```java

        BatchLoader<Long, User> userBatchLoader = new BatchLoader<Long, User>() {
            @Override
            public CompletionStage<List<User>> load(List<Long> userIds) {
                return CompletableFuture.supplyAsync(() -> {
                    return userManager.loadUsersById(userIds);
                });
            }
        };

        DataLoader<Long, User> userLoader = DataLoaderFactory.newDataLoader(userBatchLoader);

```

You can then use it to load values which will be `CompleteableFuture` promises to values
 
```java
        CompletableFuture<User> load1 = userLoader.load(1L);
```
 
or you can use it to compose future computations as follows.  The key requirement is that you call
`dataloader.dispatch()` or its variant `dataloader.dispatchAndJoin()` at some point in order to make the underlying calls happen to the batch loader.

In this version of data loader, this does not happen automatically.  More on this in [Manual dispatching](#manual-dispatching) .

```java
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

```

As stated on the original Facebook project :

> A naive application may have issued four round-trips to a backend for the required information, 
but with DataLoader this application will make at most two.
 
> DataLoader allows you to decouple unrelated parts of your application without sacrificing the 
performance of batch data-loading. While the loader presents an API that loads individual values, all 
concurrent requests will be coalesced and presented to your batch loading function. This allows your 
application to safely distribute data fetching requirements throughout your application and 
maintain minimal outgoing data requests.

In the example above, the first call to dispatch will cause the batched user keys (1 and 2) to be fired at the BatchLoader function to load 2 users.
  
Since each `thenAccept` callback made more calls to `userLoader` to get the "user they have invited", another 2 user keys are given at the `BatchLoader` 
function for them.    

In this case the `userLoader.dispatchAndJoin()` is used to make a dispatch call, wait for it (aka join it), see if the data loader has more batched entries, (which is does)
and then it repeats this until the data loader internal queue of keys is empty.  At this point we have made 2 batched calls instead of the naive 4 calls we might have made if
we did not "batch" the calls to load data.

## Batching requires batched backing APIs

You will notice in our BatchLoader example that the backing service had the ability to get a list of users given
a list of user ids in one call.
 
```java
            public CompletionStage<List<User>> load(List<Long> userIds) {
                return CompletableFuture.supplyAsync(() -> {
                    return userManager.loadUsersById(userIds);
                });
            }
```
 
 This is important consideration.  By using `dataloader` you have batched up the requests for N keys in a list of keys that can be 
 retrieved at one time.
 
 If you don't have batched backing services, then you can't be as efficient as possible as you will have to make N calls for each key.
 
 ```java
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

```
 
That said, with key caching turn on (the default), it will still be more efficient using `dataloader` than without it.

### Calling the batch loader function with call context environment

Often there is a need to call the batch loader function with some sort of call context environment, such as the calling users security
credentials or the database connection parameters.  

You can do this by implementing a `org.dataloader.BatchLoaderContextProvider` and using one of 
the batch loading interfaces such as `org.dataloader.BatchLoaderWithContext`.

It will be given a `org.dataloader.BatchLoaderEnvironment` parameter and it can then ask it
for the context object.

```java
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
```

The batch loading code will now receive this environment object and it can be used to get context perhaps allowing it
to connect to other systems. 

You can also pass in context objects per load call.  This will be captured and passed to the batch loader function.

You can gain access to them as a map by key or as the original list of context objects.

```java
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
```

### Returning a Map of results from your batch loader

Often there is not a 1:1 mapping of your batch loaded keys to the values returned.

For example, let's assume you want to load users from a database, you could probably use a query that looks like this:

```sql
  SELECT * FROM User WHERE id IN (keys)
```
 
 Given say 10 user id keys you might only get 7 results back.  This can be more naturally represented in a map
 than in an ordered list of values from the batch loader function.
 
 You can use `org.dataloader.MappedBatchLoader` for this purpose. 
 
 When the map is processed by the `DataLoader` code, any keys that are missing in the map
 will be replaced with null values.  The semantic that the number of `DataLoader.load` requests
 are matched with an equal number of values is kept.
 
 The keys provided MUST be first class keys since they will be used to examine the returned map and
 create the list of results, with nulls filling in for missing values.
 
```java
        MappedBatchLoaderWithContext<Long, User> mapBatchLoader = new MappedBatchLoaderWithContext<Long, User>() {
            @Override
            public CompletionStage<Map<Long, User>> load(Set<Long> userIds, BatchLoaderEnvironment environment) {
                SecurityCtx callCtx = environment.getContext();
                return CompletableFuture.supplyAsync(() -> userManager.loadMapOfUsersByIds(callCtx, userIds));
            }
        };

        DataLoader<Long, User> userLoader = DataLoaderFactory.newMappedDataLoader(mapBatchLoader);

        // ...
```

### Returning a stream of results from your batch publisher

It may be that your batch loader function can use a [Reactive Streams](https://www.reactive-streams.org/) [Publisher](https://www.reactive-streams.org/reactive-streams-1.0.3-javadoc/org/reactivestreams/Publisher.html), where values are emitted as an asynchronous stream.

For example, let's say you wanted to load many users from a service without forcing the service to load all
users into its memory (which may exert considerable pressure on it).

A `org.dataloader.BatchPublisher` may be used to load this data:

```java
        BatchPublisher<Long, User> batchPublisher = new BatchPublisher<Long, User>() {
            @Override
            public void load(List<Long> userIds, Subscriber<User> userSubscriber) {
                Publisher<User> userResults = userManager.streamUsersById(userIds);
                userResults.subscribe(userSubscriber);
            }
        };
        DataLoader<Long, User> userLoader = DataLoaderFactory.newPublisherDataLoader(batchPublisher);

        // ...
```

Rather than waiting for all user values to be returned on one batch, this `DataLoader` will complete
the `CompletableFuture<User>` returned by `Dataloader#load(Long)` as each value is
published.  

This pattern means that data loader values can (in theory) be satisfied more quickly than if we wait for
all results in the batch to be retrieved and hence the overall result may finish more quickly.

If an exception is thrown, the remaining futures yet to be completed are completed
exceptionally.

You *MUST* ensure that the values are streamed in the same order as the keys provided,
with the same cardinality (i.e. the number of values must match the number of keys).

Failing to do so will result in incorrect data being returned from `DataLoader#load`.

`BatchPublisher` is the reactive version of `BatchLoader`.


### Returning a mapped stream of results from your batch publisher

Your publisher may not necessarily return values in the same order in which it processes keys and it 
may not be able to find a value for each key presented.

For example, let's say your batch publisher function loads user data which is spread across shards,
with some shards responding more quickly than others.

In instances like these, `org.dataloader.MappedBatchPublisher` can be used.

```java
        MappedBatchPublisher<Long, User> mappedBatchPublisher = new MappedBatchPublisher<Long, User>() {
            @Override
            public void load(Set<Long> userIds, Subscriber<Map.Entry<Long, User>> userEntrySubscriber) {
                Publisher<Map.Entry<Long, User>> userEntries = userManager.streamUsersById(userIds);
                userEntries.subscribe(userEntrySubscriber);
            }
        };
        DataLoader<Long, User> userLoader = DataLoaderFactory.newMappedPublisherDataLoader(mappedBatchPublisher);

        // ...
```

Like the `BatchPublisher`, if an exception is thrown, the remaining futures yet to be completed are completed
exceptionally.

Unlike the `BatchPublisher`, however, it is not necessary to return values in the same order as the provided keys,
or even the same number of values.

`MappedBatchPublisher` is the reactive version of `MappedBatchLoader`.

### Error object is not a thing in a type safe Java world

In the reference JS implementation if the batch loader returns an `Error` object back from the `load()` promise is rejected
with that error.  This allows fine grain (per object in the list) sets of error.  If I ask for keys A,B,C and B errors out the promise
for B can contain a specific error. 

This is not quite as loose in a Java implementation as Java is a type safe language.

A batch loader function is defined as `BatchLoader<K, V>` meaning for a key of type `K` it returns a value of type `V`.  

It can't just return some `Exception` as an object of type `V`.  Type safety matters.  

However, you can use the `Try` data type which can encapsulate a computation that succeeded or returned an exception.

```java
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
```

DataLoader supports this type, and you can use this form to create a batch loader that returns a list of `Try` objects, some of which may have succeeded, 
and some of which may have failed.  From that data loader can infer the right behavior in terms of the `load(x)` promise.

```java
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
```

On the above example if one of the `Try` objects represents a failure, then its `load()` promise will complete exceptionally and you can 
react to that, in a type safe manner. 

## Caching

`DataLoader` has a two tiered caching system in place.  

The first cache is represented by the interface `org.dataloader.CacheMap`.  It will cache `CompletableFuture`s by key and hence future `load(key)` calls
will be given the same future and hence the same value.

This cache can only work local to the JVM, since its caches `CompletableFuture`s which cannot be serialised across a network say.

The second level cache is a value cache represented by the interface `org.dataloader.ValueCache`.  By default, this is not enabled and is a no-op.

The value cache uses an async API pattern to encapsulate the idea that the value cache could be in a remote place such as REDIS or Memcached.

## Custom future caches

The default future cache behind `DataLoader` is an in memory `HashMap`.  There is no expiry on this, and it lives for as long as the data loader
lives.

However, you can create your own custom future cache and supply it to the data loader on construction via the `org.dataloader.CacheMap` interface.

```java
        MyCustomCache customCache = new MyCustomCache();
        DataLoaderOptions options = DataLoaderOptions.newOptions().setCacheMap(customCache).build();
        DataLoaderFactory.newDataLoader(userBatchLoader, options);
```

You could choose to use one of the fancy cache implementations from Guava or Caffeine and wrap it in a `CacheMap` wrapper ready
for data loader.  They can do fancy things like time eviction and efficient LRU caching.

As stated above, a custom `org.dataloader.CacheMap` is a local cache of `CompleteFuture`s to values, not values per se.  

If you want to externally cache values then you need to use the `org.dataloader.ValueCache` interface.

## Custom value caches

The `org.dataloader.ValueCache` allows you to use an external cache.  

The API of `ValueCache` has been designed to be asynchronous because it is expected that the value cache could be outside
your JVM.  It uses `CompleteableFuture`s to get and set values into cache, which may involve a network call and hence exceptional failures to get
or set values.

The `ValueCache` API is batch oriented, if you have a backing cache that can do batch cache fetches (such a REDIS) then you can use the `ValueCache.getValues*(`
call directly. However, if you don't have such a backing cache, then the default implementation will break apart the batch of cache value into individual requests
to `ValueCache.getValue()` for you.

This library does not ship with any implementations of `ValueCache` because it does not want to have 
production dependencies on external cache libraries, but you can easily write your own.  

The tests have an example based on [Caffeine](https://github.com/ben-manes/caffeine).


## Disabling caching 

In certain uncommon cases, a DataLoader which does not cache may be desirable. 

```java
    DataLoaderFactory.newDataLoader(userBatchLoader, DataLoaderOptions.newOptions().setCachingEnabled(false).build());
``` 

Calling the above will ensure that every call to `.load()` will produce a new promise, and requested keys will not be saved in memory.
 
However, when the memoization cache is disabled, your batch function will receive an array of keys which may contain duplicates! Each key will 
be associated with each call to `.load()`. Your batch loader MUST provide a value for each instance of the requested key as per the contract

```java
        userDataLoader.load("A");
        userDataLoader.load("B");
        userDataLoader.load("A");

        userDataLoader.dispatch();

        // will result in keys to the batch loader with [ "A", "B", "A" ]

``` 

 
More complex cache behavior can be achieved by calling `.clear()` or `.clearAll()` rather than disabling the cache completely. 
 

## Caching errors
 
If a batch load fails (that is, a batch function returns a rejected CompletionStage), then the requested values will not be cached. 
However, if a batch function returns a `Try` or `Throwable` instance for an individual value, then that will be cached to avoid frequently loading 
the same problem object.
 
In some circumstances you may wish to clear the cache for these individual problems: 

```java
        userDataLoader.load("r2d2").whenComplete((user, throwable) -> {
            if (throwable != null) {
                userDataLoader.clear("r2dr");
                throwable.printStackTrace();
            } else {
                processUser(user);
            }
        });
```


## Statistics on what is happening

`DataLoader` keeps statistics on what is happening.  It can tell you the number of objects asked for, the cache hit number, the number of objects
asked for via batching and so on.

Knowing what the behaviour of your data is important for you to understand how efficient you are in serving the data via this pattern.


```java
        Statistics statistics = userDataLoader.getStatistics();
        
        System.out.println(format("load : %d", statistics.getLoadCount()));
        System.out.println(format("batch load: %d", statistics.getBatchLoadCount()));
        System.out.println(format("cache hit: %d", statistics.getCacheHitCount()));
        System.out.println(format("cache hit ratio: %d", statistics.getCacheHitRatio()));

```

`DataLoaderRegistry` can also roll up the statistics for all data loaders inside it.

You can configure the statistics collector used when you build the data loader

```java
        DataLoaderOptions options = DataLoaderOptions.newOptions().setStatisticsCollector(() -> new ThreadLocalStatisticsCollector()).build();
        DataLoader<String,User> userDataLoader = DataLoaderFactory.newDataLoader(userBatchLoader,options);

```

Which collector you use is up to you.  It ships with the following: `SimpleStatisticsCollector`, `ThreadLocalStatisticsCollector`, `DelegatingStatisticsCollector` 
and `NoOpStatisticsCollector`.

## The scope of a data loader is important

If you are serving web requests then the data can be specific to the user requesting it.  If you have user specific data
then you will not want to cache data meant for user A to then later give it user B in a subsequent request.

The scope of your `DataLoader` instances is important.  You will want to create them per web request to ensure data is only cached within that
web request and no more.

If your data can be shared across web requests then use a custom `org.dataloader.ValueCache` to keep values in a common place.  

Data loaders are stateful components that contain promises (with context) that are likely share the same affinity as the request.

## Manual dispatching

The original [Facebook DataLoader](https://github.com/facebook/dataloader) was written in Javascript for NodeJS. 

NodeJS is single-threaded in nature, but simulates asynchronous logic by invoking functions on separate threads in an event loop, as explained
[in this post](http://stackoverflow.com/a/19823583/3455094) on StackOverflow.

NodeJS generates so-call 'ticks' in which queued functions are dispatched for execution, and Facebook `DataLoader` uses
the `nextTick()` function in NodeJS to _automatically_ dequeue load requests and send them to the batch execution function 
for processing.

Here there is an **IMPORTANT DIFFERENCE** compared to how `java-dataloader` operates!!

In NodeJS the batch preparation will not affect the asynchronous processing behaviour in any way. It will just prepare
batches in 'spare time' as it were.

This is different in Java as you will actually _delay_ the execution of your load requests, until the moment where you make a 
call to `dataLoader.dispatch()`.

Does this make Java `DataLoader` any less useful than the reference implementation? We would argue this is not the case,
and there are also gains to this different mode of operation:

- In contrast to the NodeJS implementation _you_ as developer are in full control of when batches are dispatched
- You can attach any logic that determines when a dispatch takes place
- You still retain all other features, full caching support and batching (e.g. to optimize message bus traffic, GraphQL query execution time, etc.)

However, with batch execution control comes responsibility! If you forget to make the call to `dispatch()` then the futures
in the load request queue will never be batched, and thus _will never complete_! So be careful when crafting your loader designs.

## The BatchLoader Scheduler

By default, when `dataLoader.dispatch()` is called, the `BatchLoader` / `MappedBatchLoader` function will be invoked
immediately.  

However, you can provide your own `BatchLoaderScheduler` that allows this call to be done some time into
the future.  

You will be passed a callback (`ScheduledBatchLoaderCall` / `ScheduledMapBatchLoaderCall`) and you are expected
to eventually call this callback method to make the batch loading happen.

The following is a `BatchLoaderScheduler` that waits 10 milliseconds before invoking the batch loading functions.

```java
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
```

You are given the keys to be loaded and an optional `BatchLoaderEnvironment` for informative purposes.  You can't change the list of 
keys that will be loaded via this mechanism say.

Also note, because there is a max batch size, it is possible for this scheduling to happen N times for a given `dispatch()`
call.  The total set of keys will be sliced into batches themselves and then the `BatchLoaderScheduler` will be called for
each batch of keys.  

Do not assume that a single call to `dispatch()` results in a single call to `BatchLoaderScheduler`.

This code is inspired from the scheduling code in the [reference JS implementation](https://github.com/graphql/dataloader#batch-scheduling)

## Scheduled Registry Dispatching

`ScheduledDataLoaderRegistry` is a registry that allows for dispatching to be done on a schedule. It contains a
predicate that is evaluated (per data loader contained within) when `dispatchAll` is invoked.

If that predicate is true, it will make a `dispatch` call on the data loader, otherwise is will schedule a task to
perform that check again. Once a predicate evaluated to true, it will not reschedule and another call to
`dispatchAll` is required to be made.

This allows you to do things like "dispatch ONLY if the queue depth is > 10 deep or more than 200 millis have passed
since it was last dispatched".

```java

        DispatchPredicate depthOrTimePredicate = DispatchPredicate
            .dispatchIfDepthGreaterThan(10)
            .or(DispatchPredicate.dispatchIfLongerThan(Duration.ofMillis(200)));

        ScheduledDataLoaderRegistry registry = ScheduledDataLoaderRegistry.newScheduledRegistry()
            .dispatchPredicate(depthOrTimePredicate)
            .schedule(Duration.ofMillis(10))
            .register("users",userDataLoader)
            .build();
```

The above acts as a kind of minimum batch depth, with a time overload. It won't dispatch if the loader depth is less
than or equal to 10 but if 200ms pass it will dispatch.

## Chaining DataLoader calls

It's natural to want to have chained `DataLoader` calls.

```java
        CompletableFuture<Object> chainedCalls = dataLoaderA.load("user1")
        .thenCompose(userAsKey -> dataLoaderB.load(userAsKey));
```

However, the challenge here is how to be efficient in batching terms.

This is discussed in detail in the  https://github.com/graphql-java/java-dataloader/issues/54 issue.

Since CompletableFuture's are async and can complete at some time in the future, when is the best time to call
`dispatch` again when a load call has completed to maximize batching?

The most naive approach is to immediately dispatch the second chained call as follows :

```java
        CompletableFuture<Object> chainedWithImmediateDispatch = dataLoaderA.load("user1")
                .thenCompose(userAsKey -> {
                    CompletableFuture<Object> loadB = dataLoaderB.load(userAsKey);
                    dataLoaderB.dispatch();
                    return loadB;
                });
```

The above will work however the window of batching together multiple calls to `dataLoaderB` will be very small and since
it will likely result in batch sizes of 1.

This is a very difficult problem to solve because you have to balance two competing design ideals which is to maximize the 
batching window of secondary calls in a small window of time so you customer requests don't take longer than necessary.

* If the batching window is wide you will maximize the number of keys presented to a `BatchLoader` but your request latency will increase.

* If the batching window is narrow you will reduce your request latency, but also you will reduce the number of keys presented to a `BatchLoader`.


### ScheduledDataLoaderRegistry ticker mode

The `ScheduledDataLoaderRegistry` offers one solution to this called "ticker mode" where it will continually reschedule secondary
`DataLoader` calls after the initial `dispatch()` call is made.

The batch window of time is controlled by the schedule duration setup at when the `ScheduledDataLoaderRegistry` is created.

```java
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

        ScheduledDataLoaderRegistry registry = ScheduledDataLoaderRegistry.newScheduledRegistry()
        .register("a", dataLoaderA)
        .register("b", dataLoaderB)
        .scheduledExecutorService(executorService)
        .schedule(Duration.ofMillis(10))
        .tickerMode(true) // ticker mode is on
        .build();

        CompletableFuture<Object> chainedCalls = dataLoaderA.load("user1")
        .thenCompose(userAsKey -> dataLoaderB.load(userAsKey));

```
When ticker mode is on the chained dataloader calls will complete but the batching window size will depend on how quickly
the first level of `DataLoader` calls returned compared to the `schedule` of the `ScheduledDataLoaderRegistry`.

If you use ticker mode, then you MUST `registry.close()` on the `ScheduledDataLoaderRegistry` at the end of the request (say) otherwise
it will continue to reschedule tasks to the `ScheduledExecutorService` associated with the registry.

You will want to look at sharing the `ScheduledExecutorService` in some way between requests when creating the `ScheduledDataLoaderRegistry`
otherwise you will be creating a thread per `ScheduledDataLoaderRegistry` instance created and with enough concurrent requests
you may create too many threads.

### ScheduledDataLoaderRegistry dispatching algorithm

When ticker mode is **false** the `ScheduledDataLoaderRegistry` algorithm is as follows :

* Nothing starts scheduled - some code must call `registry.dispatchAll()` a first time
* Then for every `DataLoader` in the registry
  * The `DispatchPredicate` is called to test if the data loader should be dispatched
    * if it returns **false** then a task is scheduled to re-evaluate this specific dataloader in the near future 
    * If it returns **true**, then `dataLoader.dispatch()` is called and the dataloader is not rescheduled again
* The re-evaluation tasks are run periodically according to the `registry.getScheduleDuration()`

When ticker mode is **true** the `ScheduledDataLoaderRegistry` algorithm is as follows:

* Nothing starts scheduled - some code must call `registry.dispatchAll()` a first time
* Then for every `DataLoader` in the registry
    * The `DispatchPredicate` is called to test if the data loader should be dispatched
        * if it returns **false** then a task is scheduled to re-evaluate this specific dataloader in the near future
        * If it returns **true**, then `dataLoader.dispatch()` is called **and** a task is scheduled to re-evaluate this specific dataloader in the near future
* The re-evaluation tasks are run periodically according to the `registry.getScheduleDuration()`

## Instrumenting the data loader code

A `DataLoader` can have a `DataLoaderInstrumentation` associated with it.  This callback interface is intended to provide
insight into working of the `DataLoader` such as how long it takes to run or to allow for logging of key events.

You set the `DataLoaderInstrumentation` into the `DataLoaderOptions` at build time.

```java


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
        
```

The example shows how long the overall `DataLoader` dispatch takes or how long the batch loader takes to run.

### Instrumenting the DataLoaderRegistry

You can also associate a `DataLoaderInstrumentation` with a `DataLoaderRegistry`.  Every `DataLoader` registered will be changed so that the registry
`DataLoaderInstrumentation` is associated with it.  This allows you to set just the one `DataLoaderInstrumentation` in place and it applies to all 
data loaders.

```java
    DataLoader<String, User> userDataLoader = DataLoaderFactory.newDataLoader(userBatchLoader);
    DataLoader<String, User> teamsDataLoader = DataLoaderFactory.newDataLoader(teamsBatchLoader);
    
    DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
            .instrumentation(timingInstrumentation)
            .register("users", userDataLoader)
            .register("teams", teamsDataLoader)
            .build();
    
    DataLoader<String, User> changedUsersDataLoader = registry.getDataLoader("users");
```

The `timingInstrumentation` here will be associated with the `DataLoader` under the key  `users` and the key `teams`.  Note that since
DataLoader is immutable, a new changed object is created so you must use the registry to get the `DataLoader`.  


## Other information sources

- [Facebook DataLoader Github repo](https://github.com/facebook/dataloader)
- [Facebook DataLoader code walkthrough on YouTube](https://youtu.be/OQTnXNCDywA)
- [Using DataLoader and GraphQL to batch requests](http://gajus.com/blog/9/using-dataloader-to-batch-requests)

## Contributing

All your feedback and help to improve this project is very welcome. Please create issues for your bugs, ideas and
enhancement requests, or better yet, contribute directly by creating a PR.

When reporting an issue, please add a detailed instruction, and if possible a code snippet or test that can be used
as a reproducer of your problem.

When creating a pull request, please adhere to the current coding style where possible, and create tests with your
code so it keeps providing an excellent test coverage level. PR's without tests may not be accepted unless they only
deal with minor changes.

## Acknowledgements

This library was originally written for use within a [VertX world](http://vertx.io/) and it used the vertx-core `Future` classes to implement
itself.  All the heavy lifting has been done by this project : [vertx-dataloader](https://github.com/engagingspaces/vertx-dataloader)
including the extensive testing (which itself came from Facebook).

This particular port was done to reduce the dependency on Vertx and to write a pure Java 11 implementation with no dependencies and also
to use the more normative Java CompletableFuture.  

[vertx-core](http://vertx.io/docs/vertx-core/java/) is not a lightweight library by any means so having a pure Java 11 implementation is 
very desirable.


This library is entirely inspired by the great works of [Lee Byron](https://github.com/leebyron) and
[Nicholas Schrock](https://github.com/schrockn) from [Facebook](https://www.facebook.com/) whom we would like to thank, and
especially @leebyron for taking the time and effort to provide 100% coverage on the codebase. The original set of tests 
were also ported.



## Licensing

This project is licensed under the
[Apache Commons v2.0](https://www.apache.org/licenses/LICENSE-2.0) license.

Copyright &copy; 2016 Arnold Schrijver, 2017 Brad Baker and others
[contributors](https://github.com/graphql-java/java-dataloader/graphs/contributors)

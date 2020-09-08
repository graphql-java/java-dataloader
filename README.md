# java-dataloader

[![Build Status](https://travis-ci.org/graphql-java/java-dataloader.svg?branch=master)](https://travis-ci.org/graphql-java/java-dataloader.svg?branch=master)&nbsp;&nbsp;
[![Apache licensed](https://img.shields.io/hexpm/l/plug.svg?maxAge=2592000)](https://github.com/graphql-java/java-dataloader/blob/master/LICENSE)&nbsp;&nbsp;
[![Download](https://api.bintray.com/packages/graphql-java/graphql-java/java-dataloader/images/download.svg)](https://bintray.com/graphql-java/graphql-java/java-dataloader/_latestVersion)

This small and simple utility library is a pure Java 8 port of [Facebook DataLoader](https://github.com/facebook/dataloader). 

It can serve as integral part of your application's data layer to provide a
consistent API over various back-ends and reduce message communication overhead through batching and caching.

An important use case for `java-dataloader` is improving the efficiency of GraphQL query execution.  Graphql fields
are resolved in a independent manner and with a true graph of objects, you may be fetching the same object many times.  

A naive implementation of graphql data fetchers can easily lead to the dreaded  "n+1" fetch problem. 

Most of the code is ported directly from Facebook's reference implementation, with one IMPORTANT adaptation to make
it work for Java 8. ([more on this below](#manual-dispatching)).

But before reading on, be sure to take a short dive into the
[original documentation](https://github.com/facebook/dataloader/blob/master/README.md) provided by Lee Byron (@leebyron)
and Nicholas Schrock (@schrockn) from [Facebook](https://www.facebook.com/), the creators of the original data loader.

## Table of contents

- [Features](#features)
- [Examples](#examples)
- [Let's get started!](#lets-get-started)
  - [Installing](#installing)
  - [Building](#building)
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
- Can supply your own [`CacheMap<K, V>`](https://github.com/graphql-java/java-dataloader/blob/master/src/main/java/io/engagingspaces/vertx/dataloader/CacheMap.java) implementations
- Has very high test coverage (see [Acknowledgements](#acknowlegdements))

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

        DataLoader<Long, User> userLoader = DataLoader.newDataLoader(userBatchLoader);

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

>A naive application may have issued four round-trips to a backend for the required information, 
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
 
 If you don't have batched backing services, then you cant be as efficient as possible as you will have to make N calls for each key.
 
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
                .setBatchLoaderContextProvider(() -> SecurityCtx.getCallingUserCtx());

        BatchLoaderWithContext<String, String> batchLoader = new BatchLoaderWithContext<String, String>() {
            @Override
            public CompletionStage<List<String>> load(List<String> keys, BatchLoaderEnvironment environment) {
                SecurityCtx callCtx = environment.getContext();
                return callDatabaseForResults(callCtx, keys);
            }
        };

        DataLoader<String, String> loader = DataLoader.newDataLoader(batchLoader, options);
```

The batch loading code will now receive this environment object and it can be used to get context perhaps allowing it
to connect to other systems. 

You can also pass in context objects per load call.  This will be captured and passed to the batch loader function.

You can gain access to them as a map by key or as the original list of context objects.

```java
        DataLoaderOptions options = DataLoaderOptions.newOptions()
                .setBatchLoaderContextProvider(() -> SecurityCtx.getCallingUserCtx());

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

        DataLoader<String, String> loader = DataLoader.newDataLoader(batchLoader, options);
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

        DataLoader<Long, User> userLoader = DataLoader.newMappedDataLoader(mapBatchLoader);

        // ...
```

### Error object is not a thing in a type safe Java world

In the reference JS implementation if the batch loader returns an `Error` object back from the `load()` promise is rejected
with that error.  This allows fine grain (per object in the list) sets of error.  If I ask for keys A,B,C and B errors out the promise
for B can contain a specific error. 

This is not quite as loose in a Java implementation as Java is a type safe language.

A batch loader function is defined as `BatchLoader<K, V>` meaning for a key of type `K` it returns a value of type `V`.  

It cant just return some `Exception` as an object of type `V`.  Type safety matters.  

However you can use the `Try` data type which can encapsulate a computation that succeeded or returned an exception.

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

DataLoader supports this type and you can use this form to create a batch loader that returns a list of `Try` objects, some of which may have succeeded
and some of which may have failed.  From that data loader can infer the right behavior in terms of the `load(x)` promise.

```java
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
```

On the above example if one of the `Try` objects represents a failure, then its `load()` promise will complete exceptionally and you can 
react to that, in a type safe manner. 



## Disabling caching 

In certain uncommon cases, a DataLoader which does not cache may be desirable. 

```java
    DataLoader.newDataLoader(userBatchLoader, DataLoaderOptions.newOptions().setCachingEnabled(false));
``` 

Calling the above will ensure that every call to `.load()` will produce a new promise, and requested keys will not be saved in memory.
 
However, when the memoization cache is disabled, your batch function will receive an array of keys which may contain duplicates! Each key will 
be associated with each call to `.load()`. Your batch loader should provide a value for each instance of the requested key as per the contract

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
However if a batch function returns a `Try` or `Throwable` instance for an individual value, then that will be cached to avoid frequently loading 
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
        DataLoaderOptions options = DataLoaderOptions.newOptions().setStatisticsCollector(() -> new ThreadLocalStatisticsCollector());
        DataLoader<String,User> userDataLoader = DataLoader.newDataLoader(userBatchLoader,options);

```

Which collector you use is up to you.  It ships with the following: `SimpleStatisticsCollector`, `ThreadLocalStatisticsCollector`, `DelegatingStatisticsCollector` 
and `NoOpStatisticsCollector`.

## The scope of a data loader is important

If you are serving web requests then the data can be specific to the user requesting it.  If you have user specific data
then you will not want to cache data meant for user A to then later give it user B in a subsequent request.

The scope of your `DataLoader` instances is important.  You might want to create them per web request to ensure data is only cached within that
web request and no more.

If your data can be shared across web requests then you might want to scope your data loaders so they survive longer than the web request say.

## Custom caches

The default cache behind `DataLoader` is an in memory `HashMap`.  There is no expiry on this and it lives for as long as the data loader
lives. 
 
However you can create your own custom cache and supply it to the data loader on construction via the `org.dataloader.CacheMap` interface.

```java
        MyCustomCache customCache = new MyCustomCache();
        DataLoaderOptions options = DataLoaderOptions.newOptions().setCacheMap(customCache);
        DataLoader.newDataLoader(userBatchLoader, options);
```

You could choose to use one of the fancy cache implementations from Guava or Kaffeine and wrap it in a `CacheMap` wrapper ready
for data loader.  They can do fancy things like time eviction and efficient LRU caching.

## Manual dispatching

The original [Facebook DataLoader](https://github.com/facebook/dataloader) was written in Javascript for NodeJS. NodeJS is single-threaded in nature, but simulates
asynchronous logic by invoking functions on separate threads in an event loop, as explained
[in this post](http://stackoverflow.com/a/19823583/3455094) on StackOverflow.

NodeJS generates so-call 'ticks' in which queued functions are dispatched for execution, and Facebook `DataLoader` uses
the `nextTick()` function in NodeJS to _automatically_ dequeue load requests and send them to the batch execution function 
for processing.

And here there is an **IMPORTANT DIFFERENCE** compared to how `java-dataloader` operates!!

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


## Let's get started!

### Installing

Gradle users configure the `java-dataloader` dependency in `build.gradle`:

```
repositories {
    jcenter()
}

dependencies {
    compile 'com.graphql-java:java-dataloader: 2.2.3'
}
```

### Building

To build from source use the Gradle wrapper:

```
./gradlew clean build
```


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

This particular port was done to reduce the dependency on Vertx and to write a pure Java 8 implementation with no dependencies and also
to use the more normative Java CompletableFuture.  

[vertx-core](http://vertx.io/docs/vertx-core/java/) is not a lightweight library by any means so having a pure Java 8 implementation is 
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

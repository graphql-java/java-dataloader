# java-dataloader

[![Build Status](https://travis-ci.org/graphql-java/java-dataloader.svg?branch=master)](https://travis-ci.org/graphql-java/java-dataloader.svg?branch=master)&nbsp;&nbsp;
[![Apache licensed](https://img.shields.io/hexpm/l/plug.svg?maxAge=2592000)](https://github.com/graphql-java/java-dataloader/blob/master/LICENSE)&nbsp;&nbsp;
[![Download](https://api.bintray.com/packages/bbakerman/java-dataloader/java-dataloader/images/download.svg) ](https://bintray.com/bbakerman/java-dataloader/java-dataloader/_latestVersion)

This small and simple utility library is a pure Java 8 port of [Facebook DataLoader](https://github.com/facebook/dataloader). 

It can serve as integral part of your application's data layer to provide a
consistent API over various back-ends and reduce message communication overhead through batching and caching.

An important use case for `java-dataloader` is improving the efficiency of GraphQL query execution.  Graphql fields
are resolved in a independent manner and with a true graph of objects, you may be fetching the same object many times.  

A naive implementation of graphql data fetchers can easily lead to the dreaded  "n+1" fetch problem. 

Most of the code is ported directly from Facebook's reference implementation, with one IMPORTANT adaptation to make
it work for Java 8. ([more on this below](manual-dispatching)).

But before reading on, be sure to take a short dive into the
[original documentation](https://github.com/facebook/dataloader/blob/master/README.md) provided by Lee Byron (@leebyron)
and Nicholas Schrock (@schrockn) from [Facebook](https://www.facebook.com/), the creators of the original data loader.

## Table of contents

- [Features](#features)
- [Examples](#examples)
- [Differences to reference implementation](#differences-to-reference-implementation)
  - [Manual dispatching](#manual-dispatching)
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

        DataLoader<Long, User> userLoader = new DataLoader<>(userBatchLoader);

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
  
Since each `thenAccept` callback made more calls to `userLoader` to get the "user they they invited", another 2 user keys are given at the `BatchLoader` 
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
 
That said, with key caching turn on (the default), it may still be more efficient using `dataloader` than without it.

## Differences to reference implementation

### Manual dispatching

The original data loader was written in Javascript for NodeJS. NodeJS is single-threaded in nature, but simulates
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

### Error object is not a thing in a type safe Java world

In the reference JS implementation if the batch loader returns an `Error` object back then the `loadKey()` promise is rejected
with that error.  This allows fine grain (per object in the list) sets of error.  If I ask for keys A,B,C and B errors out the promise
for B can contain a specific error. 

This is not quite as neat in a Java implementation

A batch loader function is defined as `BatchLoader<K, V>` meaning for a key of type `K` it returns a value of type `V`.  

It cant just return some `Exception` as an object of type `V` since Java is type safe.

You in order for a batch loader function to return an `Exception` it must be declared as `BatchLoader<K, Object>` which
allows both values and exceptions to be returned .  Some type safety is lost in this case if you want
to use the mix of exceptions and values pattern.

## Let's get started!

### Installing

Gradle users configure the `java-dataloader` dependency in `build.gradle`:

```
repositories {
    maven {
        jcenter()
    }
}

dependencies {
    compile 'org.dataloader:java-dataloader:1.0.0'
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
including the extensive testing.

This particular port was done to reduce the dependency on Vertx and to write a pure Java 8 implementation with no dependencies and also
to use the more normative Java CompletableFuture.  

[vertx-core](http://vertx.io/docs/vertx-core/java/) is not a lightweight library by any means
so having a pure Java 8 implementation is very desirable.


This library is entirely inspired by the great works of [Lee Byron](https://github.com/leebyron) and
[Nicholas Schrock](https://github.com/schrockn) from [Facebook](https://www.facebook.com/) whom we would like to thank, and
especially @leebyron for taking the time and effort to provide 100% coverage on the codebase. The original set of tests 
were also ported.



## Licensing

This project is licensed under the
[Apache Commons v2.0](https://www.apache.org/licenses/LICENSE-2.0) license.

Copyright &copy; 2016 Arnold Schrijver, 2017 Brad Baker and others
[contributors](https://github.com/graphql-java/java-dataloader/graphs/contributors)

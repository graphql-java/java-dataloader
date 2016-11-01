# Vert.x `DataLoader`

[![Build Status](https://travis-ci.org/engagingspaces/vertx-dataloader.svg?branch=master)](https://travis-ci.org/engagingspaces/vertx-dataloader/)&nbsp;&nbsp;
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/ec906aa3a12147e28b69b93e3a9d9bf7)](https://www.codacy.com/app/engagingspaces/vertx-dataloader?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=engagingspaces/vertx-dataloader&amp;utm_campaign=Badge_Grade)&nbsp;&nbsp;
[![Apache licensed](https://img.shields.io/hexpm/l/plug.svg?maxAge=2592000)](https://github.com/engagingspaces/vertx-dataloader/blob/master/LICENSE)&nbsp;&nbsp;
[ ![Download](https://api.bintray.com/packages/engagingspaces/maven/vertx-dataloader/images/download.svg) ](https://bintray.com/engagingspaces/maven/vertx-dataloader/_latestVersion)

![vertx-dataloader-concepts](https://cloud.githubusercontent.com/assets/5111931/17837825/f5748bfc-67bd-11e6-9c7a-d407bb92c3d9.png)

This small and simple utility library is a port of [Facebook DataLoader](https://github.com/facebook/dataloader)
to Java 8 for use with [Vert.x](http://vertx.io). It can serve as integral part of your application's data layer to provide a
consistent API over various back-ends and reduce message communication overhead through batching and caching.

An important use case for `DataLoader` is improving the efficiency of GraphQL query execution, but there are
many other use cases where you can benefit from using this utility.

Most of the code is ported directly from Facebook's reference implementation, with one IMPORTANT adaptation to make
it work for Java 8 and Vert.x. ([Find more on this in the paragraphs below]).

But before reading on, be sure to take a short dive into the
[original documentation](https://github.com/facebook/dataloader/blob/master/README.md) provided by Lee Byron (@leebyron)
and Nicholas Schrock (@schrockn) from [Facebook](https://www.facebook.com/), the creators of the original data loader.

## Table of contents

- [Features](#features)
- [Differences to reference implementation](#differences-to-reference-implementation)
  - [Manual dispatching](#manual-dispatching)
  - [Additional features!](#additional-features)
- [Let's get started!](#lets-get-started)
  - [Installing](#installing)
  - [Building](#building)
- [Project plans](#project-plans)
  - [Current releases](#current-releases)
  - [Known issues](#known-issues)
  - [Upcoming features](#upcoming-features)
  - [Future ideas](#future-ideas)
- [Other information sources](#other-information-sources)
- [Contributing](#contributing)
- [Acknowledgements](#acknowledgements)
- [Licensing](#licensing)

## Features

Vert.x `DataLoader` is a feature-complete port of the Facebook reference implementation with [one major difference](#manual-dispatching). These features are:

- Simple, intuitive API, using generics and fluent coding
- Define batch load function with lambda expression
- Schedule a load request in queue for batching
- Add load requests from anywhere in code
- Request returns [`Future<V>`](http://vertx.io/docs/apidocs/io/vertx/core/Future.html) of requested value
- Can create multiple requests at once, returns [`CompositeFuture`](http://vertx.io/docs/apidocs/io/vertx/core/CompositeFuture.html)
- Caches load requests, so data is only fetched once
- Can clear individual cache keys, so data is fetched on next batch queue dispatch
- Can prime the cache with key/values, to avoid data being fetched needlessly
- Can configure cache key function with lambda expression to extract cache key from complex data loader key types
- Dispatch load request queue after batch is prepared, also returns [`CompositeFuture`](http://vertx.io/docs/apidocs/io/vertx/core/CompositeFuture.html)
- Individual batch futures complete / resolve as batch is processed
- `CompositeFuture`s results are ordered according to insertion order of load requests
- Deals with partial errors when a batch future fails
- Can disable batching and/or caching in configuration
- Can supply your own [`CacheMap<K, V>`](https://github.com/engagingspaces/vertx-dataloader/blob/master/src/main/java/io/engagingspaces/vertx/dataloader/CacheMap.java) implementations
- Has very high test coverage (see [Acknowledgements](#acknowlegdements))

Then Vert.x `DataLoader` has some very interesting [Additional features](#additional-features) that result from it being based on Vert.x

## Differences to reference implementation

### Manual dispatching

The original data loader was written in Javascript for NodeJS. NodeJS is single-threaded in nature, but simulates
asynchronous logic by invoking functions on separate threads in an event loop, as explained
[in this post](http://stackoverflow.com/a/19823583/3455094) on StackOverflow.

[Vert.x](http://vertx.io) on the other hand also uses an event loop ([that you should not block!!](http://vertx.io/docs/vertx-core/java/#golden_rule)), but comes
with actor-like [`Verticle`](http://vertx.io/docs/vertx-core/java/#_verticles)s and a
distributed [`EventBus`](http://vertx.io/docs/vertx-core/java/#event_bus) that make it inherently asynchronous, and non-blocking.

Now in NodeJS generates so-call 'ticks' in which queued functions are dispatched for execution, and Facebook `DataLoader` uses
the `nextTick()` function in NodeJS to _automatically_ dequeue load requests and send them to the batch execution function for processing.

And here there is an **IMPORTANT DIFFERENCE** compared to how _this_ data loader operates!!

In NodeJS the batch preparation will not affect the asynchronous processing behaviour in any way. It will just prepare
batches in 'spare time' as it were.

This is different in Vert.x as you will actually _delay_ the execution of your load requests, until the moment where you make a call
to `dataLoader.dispatch()` in comparison to when you would just handle futures directly.

Does this make Java `DataLoader` any less useful than the reference implementation? I would argue this is not the case,
and there are also gains to this different mode of operation:

- In contrast to the NodeJS implementation _you_ as developer are in full control of when batches are dispatched
- You can attach any logic that determines when a dispatch takes place
- You still retain all other features, full caching support and batching (e.g. to optimize message bus traffic, GraphQL query execution time, etc.)

However, with batch execution control comes responsibility! If you forget to make the call to `dispatch()` then the futures
in the load request queue will never be batched, and thus _will never complete_! So be careful when crafting your loader designs.

**Note**: In future releases the danger of not invoking dispatch will be greatly diminished. There will be an optional dispatch timeout,
and some other optional features that ensure all load requests eventually complete. See [Project plans](#project-plans) for upcoming features and ideas.

### Additional features

- Initial release is a feature-complete port of the reference implementation (only change being [Manual dispatching](#manual-dispatching)).
- Sorry, no additional features yet :flushed:, but this is where _you_ :hand: come into the picture: By giving your [feedback](https://github.com/vertx-dataloader/issues) and [contribute](#contributing)! You are most welcome.
- See [Project plans](#project-plans) for [upcoming features](#upcoming-features) and [future ideas](#future-ideas).

## Let's get started!

### Installing

Gradle users configure the `vertx-dataloader` dependency in `build.gradle`:

```
repositories {
    maven {
        jcenter()
    }
}

dependencies {
    compile 'io.engagingspaces:vertx-dataloader:0.9.0'
}
```

### Building

To build from source use the Gradle wrapper:

```
./gradlew clean build
```

Or when using Maven add the following repository to your `pom.xml`:

```
<repositories>
    <repository>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
        <id>central</id>
        <name>bintray</name>
        <url>http://jcenter.bintray.com</url>
    </repository>
</repositories>
```

And add the dependency to `vertx-dataloader`:

```
<dependency>
    <groupId>io.engagingspaces</groupId>
    <artifactId>vertx-dataloader</artifactId>
    <version>0.9.0</version>
    <type>pom</type>
</dependency>
```

## Project plans

### Current releases

- `1.0.0` Initial release

### Known issues

- Tests on job queue ordering need refactoring to Futures, one test currently omitted

### Upcoming features

- Distributed cache

### Future ideas

- `CompletableFuture` implementation

## Other information sources

- [Facebook DataLoader Github repo](https://github.com/facebook/dataloader)
- [Facebook DataLoader code walkthrough on YouTube](https://youtu.be/OQTnXNCDywA)
- [Using DataLoader and GraphQL to batch requests](http://gajus.com/blog/9/using-dataloader-to-batch-requests)

## Contributing

All your feedback and help to improve this project is very welcome. Please create issues for your bugs, ideas and
enhancement requests, or better yet, contribute directly by creating a PR.

When reporting an issue, please add a detailed instruction, and if possible a code snippet or test that can be used
as a reproducer of your problem.

When creating a pull request, please adhere to the Vert.x coding style where possible, and create tests with your
code so it keeps providing an excellent test coverage level. PR's without tests may not be accepted unless they only
deal with minor changes.

## Acknowledgements

This library is entirely inspired by the great works of [Lee Byron](https://github.com/leebyron) and
[Nicholas Schrock](https://github.com/schrockn) from [Facebook](https://www.facebook.com/) whom I like to thank, and
especially @leebyron for taking the time and effort to provide 100% coverage on the codebase. A set of tests which
I also ported.

## Licensing

This project [vertx-dataloader](https://github.com/engagingspaces/vertx-dataloader) is licensed under the
[Apache Commons v2.0](https://github.com/engagingspaces/vertx-dataloader/LICENSE) license.

Copyright &copy; 2016 Arnold Schrijver and other
[contributors](https://github.com/engagingspaces/vertx-dataloader/graphs/contributors)

# Vert.x `DataLoader`

**More documentation coming soon..**

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
  - [Additional features](#additional-features)
- [Let's get started!](#lets-get-started)
  - [Installing](#installing)
  - [Building](#building)
  - [Using](#using)
  - [JavaDoc](#javadoc)
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

- Yes, they will be listed here :)

## Differences to reference implementation

### Manual dispatching

### Additional features

- None, so far :(

## Let's get started!

### Installing

### Building

### Using

### JavaDoc

## Project plans

### Current releases

- Not yet released

### Known issues

- ** Work in progress..**
- Not yet production-ready as of yet, still porting tests that may uncover bugs.

### Upcoming features

### Future ideas

## Other information sources

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
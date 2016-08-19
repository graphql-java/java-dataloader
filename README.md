# Vert.x DataLoader

**Work in progress. More documentation coming soon..**

DataLoader is a generic utility to be used as part of your Vert.x application's batch data fetching layer to provide a
consistent API over various back-ends and reduce requests to those back-ends via batching and caching.

The code is a direct port of the [facebook/dataloader](https://github.com/facebook/dataloader) reference
implementation provided by Facebook, with some slight modifications to make it work for Java 8
and [Vert.x](http://vertx.io).

Be sure to check the original documentation: https://github.com/facebook/dataloader/blob/master/README.md
And this post explaining how to use data loader with GraphQL: http://gajus.com/blog/9/using-dataloader-to-batch-requests

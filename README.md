## Prometheus exporter

This is a state of the art akka based prometheus exporter implementation.

It's mostly conforms the specification. Namely [this](https://prometheus.io/docs/instrumenting/writing_exporters/) and [this](https://prometheus.io/docs/instrumenting/exposition_formats/).

With some love this could be a usable lib, but right now this is not a production ready thing! If you want to use this in close to production, pls contact me, so we can improve this!


### How it works:

You can spawn a new `Metric`. This metric is a wrapper to one or more actors. The metric has an enduser API.

Metrics call an underlaying actor, which makes the metric call fast and threadsafe! (Also introduce inconsistency, nondeterminism and race conditions! :D )

The actors are managing states in a good (threadsafe) way. If you are not familiar with the concept read the wikipedia!

The actors pushes the actual state to a `Registry`.

The registry is implemented as an akka stream. It merges the metrics to a big map, and gives an interface to query the actual state, as a prometheus export output.


### Performance:

For the initial testings it is not bad, but for more than 10 metrics/sec you probably want to stresstest it!
 
### TODOS:

Before production use it would be nice to:
 - write tests
 - make it `F` based instead of `Future`
 - stresstest it
 - make the akka deps more transparent

Also it would be interesting to move to a full `IO` or `ZIO` implementation.

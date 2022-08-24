# Thread Pool Size

## Summary

Increasing the thread pool size could reduce the time taken to send messages to apple and google.

By specifying the thread pool size as a configurable variable it enabled us to quickly and easily change the value. This implementation reduces risk if deployed to production.

It's suggested that we deploy the configurable thread pool size into production with an initial conservative value, gradually increasing this while monitoring memory usage, execution times and errors.

## Background

One of the bottlenecks in our notification send architecture is the group of lambdas that send notifications to apple and google.

These lambdas are responsible for receiving a message containing a batch of tokens and forwarding these to the relevant servers (which then deliver the notification to devices).

The lambdas are written in scala and make use of streams and concurrency in the send process. At the moment we use the default execution context for these functions, which use the default thread pool size.

In scala the thread pool is a group of resources that allow for operations to be parallelised. By default, scala sets the thread pool size to be the number of CPUs.

We tested the impact of increasing the thread pool size beyond what we understood to be the default (5 for lambdas). Throughout all testing the memory utilisation never exceeded 12% of memory allocated, and we never received any errors in the log messages.

| Thread pool size | Time between notification request received and last sender finished (s) |
|:-----------------|:------------------------------------------------------------------------|
| 5                | 115                                                                     |
| 10               | 103                                                                     |
| 50               | 76                                                                      |
| 100              | 48                                                                      |
| 200              | 40                                                                      |
| 400              | 41                                                                      |


## Test Setup

In the android and ios sender lambdas we provided an override for the default execution context:

```scala
  override implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(config.threadPoolSize))
```

We defined the thread pool size as a parameter in the aws systems manager and extended our config definition, eg:

```scala
  def fetchApns(): ApnsWorkerConfiguration = {
    val config = fetchConfiguration(confPrefixFromPlatform)
    ApnsWorkerConfiguration(
      config.getString("cleaningSqsUrl"),
      ApnsConfig(
        teamId = config.getString("apns.teamId"),
        bundleId = config.getString("apns.bundleId"),
        keyId = config.getString("apns.keyId"),
        certificate = config.getString("apns.certificate"),
        mapiBaseUrl = config.getString("mapi.baseUrl"),
        sendingToProdServer = config.getBoolean("apns.sendingToProdServer"),
        dryRun = config.getBoolean("dryrun")
      )
      ),
      config.getInt("apns.threadPoolSize")
    )
  }
```

We carried out all our testing in CODE, running tests with different values of thread pool size.

All our testing in CODE used the `dryRun` functionality to prevent actual messages sending to APNS/FCM. We added a `Thread.sleep(300)` to simulate the delay we'd expect to see in PROD if not using `dryRun`.
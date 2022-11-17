
# Cache tokens in Notification API

One of the main tasks in the notification delivery pipeline is the harvester job, which retrieves the list of targets device tokens and passes them on to the task, the sender worker.

Based on some of the [experiements](04-harvester-lambda-timing.md), the bottleneck of the harvester task at the moment is that the concurrency of the lambda execution does not grow fast enough.

We have done some research into the feasibility of having all the tokens in the memory cache of the Notifications API.  If it is possible, the Notifications API can directly write the list of tokens to the SQS for the sender lambda (instead of a list of shard ranges for harvester), thus eliminating the harvester task altogether.

## Experiment and result

The main goal of the experiment is to check if it is feasible to keep a cached copy of tokens in the memory.

In the Notifications API, I scheduled an asynchronous job every 10 minutes to query the database for all breaking news (uk, us, au and international) registrations and keep the result sets (around 3.3 millions) in a mutable list.  An endpoint was created to get the number of registrations stored in the list as well as the memory usage of the JVM.  The instance type was changed to `t4g.medium`, which has 4GB memory, from t4g.micro (1 GB memory).  JVM maximum memory size was set to 2GB.

After the service has been running for a while, its heap grows to its maximum size (1951 MB), and its free memory goes between 300 MB and 600 MB.

I tried another Notifications API with the same setup except without querying the database.  Its heap size stays at 300 MB (the maximum size was set to 1951 MB) and its free memory is around 100 MB - 200 MB.

The source code is in [this branch](https://github.com/guardian/mobile-n10n/tree/LIVE-4580-study-keep-tokens-in-memory).

I did not send any requests to the service in either of scenarios.

## Analysis

1. Based on the test result, if we assume that the Notifications API normally takes around 100 MB memory and it takes an additional 1.5 GB memory to cache the 3.3 million registrations, it appears that it may require and additional 3.5 GB memory to cache 7.7 million registrations.

2. A big EC2 instance class is required.  For reference, `tg4.large` provides 8 GB memory.

3. The registrations table is expected to be growing in size.  So we need to prepare a larger memory to accommodate the growing data and have some monitoring tools in place before out of memory errors happen.

4. Frequent GC may happen due to frequent changes to huge data structure in JVM heap, but it was not tested.

5. Maybe we just keep the breaking news registrations in cache only?  
- But then there will be two different program flows, one for breaking news and one for others.  It may complicate the design.

6. Even if the tokens are in memory, the notifications API may need to do further processing.  For example, if the notification is to be sent to multiple topics, it has to merge the list of tokens from multiple topics and remove duplicates


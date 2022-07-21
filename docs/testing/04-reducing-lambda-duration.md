# Reducing Lambda Duration

This document summarises the research and initial conclusions for reducing lambda init duration.

## Summary

Increasing the memory allocated to our lambdas:
- Reduced processing duration
- For s3 lambdas, reduced init duration
- For ecr lambdas, had no impact on init duration

For a 40% cost increase we could reduce the total time of the harvesters and workers by 15%.

To get the best performance improvement from increasing memory we should consider moving to s3 lambdas and reducing the package size as much as possible.

## Background

One of the factors impacting the throughput of notifications is the duration of the harvester and worker lambdas. Without changing any code we could impact the lambda duration by:
- Increasing the lambda memory: increasing the memory increases the CPU allocated to the lambda and should reduce processing time.
- Reducing the init duration: this is the time taken for a lambda to initialise from a cold start. We can reduce the cold start time by increasing memory, reducing package size and changing whether we load the zip/image from s3 or ecr.

We [migrated](https://github.com/guardian/mobile-n10n/pull/565) the harvester and worker lambdas to ecr because the image size was getting close to the limit available to s3 lambdas. At the time of the migration, I don't believe we collected data about how this impacted speed of delivery, but the team believed that since this change the speed of delivery has worsened.

## Results

### Worker lambdas

The harvester and ios/android lambdas use the same cloudformation. They also run from the same package. They are configured with a memory of 3008MB.

The memory allocation was varied:

|Memory|Init Duration (ms)|Duration (ms)|Billed Duration (ms)|Cost per invocation ($)|% increase in $|% reduction in time|
|:----|:----|:----|:----|:----|:----|:----|
|3072|4405|4562|8969|0.0004484733333| | |
|4096|4384|3864|8270|0.0005516551769|23|8|
|5120|4072|3522|7595|0.0006326754|41|15|
|6144|4230|3305|7521|0.0007521916667|67|16|
|10240|4324|3141|7463|0.001244137667|77|16|

We can see that the init duration has no significant correlation with the memory allocation, but we can get an improvement in execution time.

Our AWS costs for all lambdas in the eu-west-1 region was $1,732 in the last 6 months, about $289/month. 

To get a 15% performance improvement by changing memory allocation alone we could estimate an increased monthly cost of $404, or $2,425.


### Fake breaking news

The fake breaking news lambda was tested to see how the performance varied for an s3 lambda, it is not being suggested to reconfigure this lambda, it was only chosen as a convient lambda to work with and test.

The lambda was packaged with "common" as a dependency which meant it included a lot of dependencies it didn't need. By removing "common" the overall package size could be reduced by 30MB (40% reduction).

This reduction in package size had only a small impact on init duration:

|Memory|Init Duration (ms)|
|:----|:----|
|70MB|4750|
|40MB|4129|


However, when varying the memory allocated to the lambda we can see that the init duration reduced as memory increased (which was not true for the harvester):

|Memory|Init Duration (ms)|Duration (ms)|Billed Duration (ms)|
|:----|:----|:----|:----|
|1024|4129|3176|3176|
|2048|3584|1845|1845|
|4096|2926|1318|1318|
|10240|2563|1380|1380|

It's also worth noting that this s3 lambda did not get billed for the init duration.

### Conclusions

We can impact lambda duration by adjusting the memory allocated, but this comes at a cost.

There may be other factors impacting the lambda duration. Thinking about s3 lambdas, the ophan airflow lambda has zip size of 70MB and allocated memory of 768MB, however its init duration is ~240ms, a factor of 10 smaller than the fake breaking news lambda after the package size reduction and memory increase.
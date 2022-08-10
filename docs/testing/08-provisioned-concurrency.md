# Provisioned Concurrency

Given we [know](07-lambda-timing.md) the sender lambdas are a current bottleneck in achieving our 90in2 target we tested the result of defining [provisioned concurrency](https://aws.amazon.com/blogs/aws/new-provisioned-concurrency-for-lambda-functions/) for these lambdas.

Defining provisioned concurrency for the ios and android sender lambdas can increase our 90in2 percentage, indicating that it's likely we do have a concurrency issue with this part of our notification architecture.

It demonstrates that, given a more optimised architecture/configuration, achieving our SLO is possible.

There are some considerations to keep in mind:
- Defining 100 provisioned execution environments for our 2 lambdas would increase our monthly cost by $3655.38/month/lambda.
- We could present this option to Editorial as a short-term way to ease the problem while we work on more sustainable (lower-cost) solutions.
- We can think about different solutions to achieving the same result, e.g. running ec2 instances instead of lambdas, increasing token batch size, increasing number of messages each lambda reads from queue (currently only 1).
- We defined provisioned concurrency manually via the aws console, these changes should really be defined in CDK. We [attempted](https://github.com/guardian/mobile-n10n/pull/702) this, but it was quite complex. If we were to define provisioned concurrency (even as a short-term solution) some more dev work would be required.
- We can see that the android sender lambda performs worse than the ios sender lambda so could consider [batching](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/messaging/FirebaseMessaging#public-batchresponse-sendall-listmessage-messages) requests to firebase.
- We saw some unexpected results when testing with a provisioned capacity of 200, we will raise a support case with AWS to validate our setup and understand our results better.

| Provisioned Concurrency | % delivered within 2 mins | Additional monthly cost |
|:------------------------|:--------------------------|:------------------------|
| None                    | 51                        | $0                      |
| 100 per lambda          | 93                        | $7,310.76               |
| 200 per lambda          | --                        | $14,621.52              |

## No provisioned concurrency

There was some metrics about our existing architecture/configuration that led us to hypothesise that we had a concurrency issue with the ios and android sender lambdas:

1. Invocation count vs concurrent executions

Our sender lambdas include metrics about the invocation count (how many times the lambda function is being executed) and the number of concurrent executions (how many lambda execution environments are running in parallel).

![Invocation count](images/no-pc-invocations.png)
![Concurrent executions](images/no-pc-concurrent-ex.png)

Given we define a reserved concurrency (i.e. the maximum number of lambda execution environments available at a given time) of 250, the number running in parallel is significantly less than our maximum. Running more execution environments concurrently should increase our throughput (reduce time taken to send notifications).

2. Time that messages spend on the queue

Our sender lambdas are triggered by an SQS queue. When messages land on this queue the sender lambdas will be invoked and will process these messages.

A metric to consider is the time that messages spend on the queue before being processed. We could see large times, meaning there were significant delays before lambdas were processing these messages. Having more lambdas available to process messages should reduce this time and increase our 90in2 percentage.

![Time spent on queue](images/no-pc-time-spent-on-queue.png)

Before changing the provisioned concurrency we collected some notification send statistics:

| notificationId                       | topic                  | total sent | within 2mins | 90 in 2 % | time sent   | total duration (ms) | title                                                                                                          |
|:-------------------------------------|:-----------------------|:-----------|:-------------|:----------|:------------|:--------------------|:---------------------------------------------------------------------------------------------------------------|
| d5f957c4-90d5-4b87-b4a8-d179725d01e5 | breaking/uk            | 896,948    | 564,619      | 63        | 09/08 08:55 | 154,317             | UK energy bills now forecast to hit £4,266 from January                                                        |
| 05b5c029-19f6-4789-aa9f-66e605c43cc8 | breaking/uk            | 889,480    | 525,795      | 59        | 09/08 09:09 | 257,965             | Issey Miyake, famed Japanese fashion designer, dies aged 84                                                    |
| cc92fd7d-0feb-4aaf-ba71-f4f0cb49b9b1 | breaking/international | 554,348    | 151,097      | 27        | 09/08 09:10 | 247,130             | Issey Miyake, famed Japanese fashion designer, dies aged 84                                                    |
| 87c19f4c-0bf7-4ddd-86b3-4acf21ba6019 | breaking/uk            | 889,414    | 496,859      | 56        | 09/08 09:32 | 249,708             | Lamont Dozier, Motown songwriter famed for hits including Baby Love and Stop in the Name of Love, dies aged 81 |
| 3c6897ea-a903-435f-b5b2-7cf9c3620900 | breaking/international | 554,562    | 181,101      | 33        | 09/08 09:33 | 260,431             | Lamont Dozier, Motown songwriter famed for hits including Baby Love and Stop in the Name of Love, dies aged 81 |
| adc6d742-4b3c-4d84-bef8-a297eda64247 | breaking/us            | 287,288    | 156,950      | 55        | 09/08 09:33 | 240,422             | Lamont Dozier, Motown songwriter famed for hits including Baby Love and Stop in the Name of Love, dies aged 81 |
| 306a0857-5bbb-424d-8dc2-df621c44326b | breaking/uk            | 883,327    | 573,251      | 65        | 09/08 10:01 | 173,542             | sport/2022/aug/09/toni-minichiello-banned-for-life-by-uk-athletics-over-sexually-inappropriate-conduct         |

## Provisioned concurrency (100 each for both the ios and android sender lambdas)

We manually configured our sender lambdas:
- defined a [version](https://docs.aws.amazon.com/lambda/latest/dg/configuration-versions.html) for each of our lambdas
- configured the provisioned concurrency for the version, which was set to 100 for this test

NB: provisioned concurrency can only be defined for a version or alias of a lambda function.

We collected notification send statistics during the testing period:

| notificationId                       | topic                  | total sent | within 2mins | 90 in 2 % | time sent   | total duration (ms) | title                                                                         |
|:-------------------------------------|:-----------------------|:-----------|:-------------|:----------|:------------|:--------------------|:------------------------------------------------------------------------------|
| e65654b0-f0dd-4fb2-bd75-463e4bd0068f | breaking/uk            | 886,121    | 770,400      | 87        | 09/08 13:47 | 141,705             | Serena Williams announces she will retire from tennis after glittering career |
| 1b730aab-308f-419b-b2ac-7920c997bfdb | breaking/us            | 292,628    | 277,288      | 95        | 09/08 13:49 | 125,882             | Serena Williams announces she will retire from tennis after glittering career |
| 155669af-127c-4a1a-9210-50c455c6a24b | breaking/international | 560,515    | 523,142      | 93        | 09/08 13:50 | 130,877             | Serena Williams announces she will retire from tennis after glittering career |
| 9c0e3710-3660-4279-b6a8-7c4a963e11de | breaking/au            | 170,519    | 166,630      | 98        | 09/08 13:55 | 34,273              | Serena Williams announces she will retire from tennis after glittering career |

The 90in2 percentage was significantly increased. The metrics confirm that we had improved the key metrics we'd identified:

![Invocations](images/100-pc-invocations.png)
![Concurrent executions](images/100-pc-concurrent-ex.png)
![Time spent on queue](images/100-pc-time-spent-on-queue.png)

The number of invocations is smaller than the example presented before, however we can still draw some rough conclusions:
- The concurrent executions are higher, and very close to the provisioned amount we specified, meaning we are successfully utilising more execution environments during message processing.
- The time spent on the queue appears much lower, but it could possibly be reduced further by increasing our provisioned concurrency.
- For notifications sent to our largest number of recipients (breaking/uk topic, 886k registered readers) we are now very close to meeting the 90in2 target

## Provisioned concurrency (200 each for both the ios and android sender lambdas)

We attempted to improve performance further by increasing the provisioned concurrency:

- Initially we manually increased the concurrency to 250 (our maximum amount), however this resulted in throttling alarms, possibly a side effect of how lambda environments are scaled in vs our maximum number of available environments.
- We successfully increased the concurrency to 200 instead.

We collected notification send statistics during the testing period but rolled back our changes quite quickly. We noticed that processing time had increased, not decreased, and we were receiving throttling alerts for our lambdas.

We will raise a support case with aws to validate our setup and results.

| notificationId                       | topic       | total sent | within 2mins | 90 in 2 % | time sent   | total duration (ms) | title                                                                                                                 |
|:-------------------------------------|:------------|:-----------|:-------------|:----------|:------------|:--------------------|:----------------------------------------------------------------------------------------------------------------------|
| 77d70574-67db-49da-9fad-82cbd892fc0a | breaking/au | 176,391    |              |           | 10/08 07:56 | 63,213              | China's ambassador has delivered a simple message to Australia - it's our way or the highway, writes Katharine Murphy |
| a710a9e6-8ede-45bc-840a-a9c8934354ba | breaking/uk | 893,542    |              |           | 10/08 11:08 | 174,803             | Polio vaccine to be offered to 900,000 children aged one to nine in London after virus found in sewers                |
| d5b3bde3-fa06-4777-a837-671efe4cff69 | breaking/us | 281,819    |              |           | 10/08 12:35 | 141,471             | US inflation falls to 8.5% in July but still close to multi-decade high                                               |

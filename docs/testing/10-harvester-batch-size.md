# Harvester batch size and shard size

In [our study](../architecture/04-harvester-lambda-timing.md) on the harvester lambda, it was found that the lambda did not scale up the harvester lambda fast enough to achieve the level of parallel processing we need.

Based on AWS recommendation, we tried increasing the batch size in an attempt to make harvester process all the messages with fewer invocations.

Since processing two messages in a call may be equivalent to processing one message with double the shard range, we also tested the performance with increased the shard range for comparison.

(Increased shard range was tested before, but with recent improvement on the database and other parts, the result may be different.)

# Set up

We sent a breaking news notification to the topics `breaking/uk`, `breaking/us`, `breaking/au` and `breaking/international`, which had a total of 3,358,659 recipients, under each of the following three configurations:

1. Batch size set to 1 (original setting)
2. Batch size set to 2
3. Batch size set to 1 but the shard size is doubled

# Result

## Batch size is 1 

The harvester processed one record of SQS message in each invocation.  Each record covers around 10,000 recipients.

| # | No. harvester invocations | Avg. processing (s) | Total harvester duration (s) | Error "marked as broken" |
| ----------- | ----------- | ----------- | ----------- | ----------- |
| 1	| 335 | 23.91 | 31.86 | 0 |
| 2	| 335 | 19.02 | 31.03 | 0 |
| 3	| 335 | 23.07 | 31.64 | 0 |
| AVG | 335| 22.00 | 31.51 | 0 |

## Batch size is 2

The harvester processed two records in each invocation.  Each record covers around 10,000 recipients.

| # | No. harvester invocations | Avg. processing (s) | Total harvester duration (s) | Error "marked as broken" |
| ----------- | ----------- | ----------- | ----------- | ----------- |
| 1	| 168 | 25.2 | 37.26 | 0 |
| 2	| 169 | 21.33 | 31.49 | 0 |
| 3	| 170 | 21.53 | 36.12 | 0 |
| AVG | 169 | 22.69 | 34.96 | 0 |

## Batch size is 1 and shard size is doubled to 20,000

The harvester processed one record in each invocation.  Each record covers around 20,000 recipients.

| # | No. harvester invocations | Avg. processing (s) | Total harvester duration (s) | Error "marked as broken" |
| ----------- | ----------- | ----------- | ----------- | ----------- |
| 1	| 168 | 24.4 | 34.96 | 0 |
| 2	| 168 | 21.39 | 35.15 | 0 |
| 3	| 168 | 31.2 | 43.01 | 0 |
| AVG | 168 | 25.66 | 37.71 | 0 |

## Conclusion

We do not notice any consistent difference between the batch size of 1 and the batch size of 2.  It suggests that the harvester processes the messages in the batch sequentially and the setting of batch size does not affect the rate of scaling up in AWS lambda. 

We also see a little degradation in performance if we processes around 20,000 tokens in one SQS record rather than processing two SQS records each with 10,000 tokens.  One of the possible reasons may be the fact that more memory is needed to keep the intermediate data when processing 20,000 tokens in one batch.

If we want to improve based on the current architecture, the harvester _may_ give better throughput if it processes SQS records in parallel, provided that it runs on a machine with multiple CPU cores.

Lambda allocates CPU power proportional to the amount of memory provisioned.  I was not able to find out from the documentation how much memory provisioned can result in one more virtual CPU allocated, but according to this [AWS document](https://aws.amazon.com/about-aws/whats-new/2020/12/aws-lambda-supports-10gb-memory-6-vcpu-cores-lambda-functions/), a lambda function can be provisioned with a maximum of 10GB memory, which leads to 6 virtual CPU allocated. 





# RDS Instance Class

## Background
We are exploring whether changing the RDS instance class to a more powerful one can improve the performance of the harvester and thus the overall performance of our notification delivery pipeline.  

## RDS Instance Classes to be tested

We performed the performance test with `t4g.medium` (which we are using), `t4g.large` and `m6g.large`.  Their specs are summarised in the table below:

Cost, on-demand, monthly, MZ deployment

| Instance class | vCPU | Memory | EBS burst bandwidth | Cost[^1] |
| -------------- | -----| ------ | ------------------- | ---- | 
| t4g.medium | 2 | 4 GB | 2,085 Mbps | 100.7400 USD |
| t4g.large | 2 | 8 GB | 2,780 Mbps | 202.2100 USD |
| m6g.large | 2 | 8 GB | 4,750 Mbps | 256.9600 USD |

[^1]: This the the monthly on-demand cost of using the RDS instance class with AZ deployment.

The `t4g.medium` and `t4g.large` are burstable instance type that provide a baseline level of CPU performance with the ability to burst CPU usage at any time for as long as required.

The instances accumulate CPU credits when a workload is operating below baseline threshold. Each earned CPU credit provides the `t4g` instance the opportunity to burst with the performance of a full CPU core for one minute when needed.  The `t4g` instances are configured for Unlimited mode, which means they can burst beyond the baseline over a 24-hour window for an additional charge.

The `m6g` instance is a general-purpose instance classes powered by AWS Graviton2 processors. These deliver balanced compute, memory, and networking for a broad range of general-purpose workloads.

## Test Setup

It was found that the harvester on CODE usually take less than 20 seconds for a notification that has around 900,000 subscribers.  In this test, we would like to measure how the harvester performs under very heavy workflow with different instance classes.

In order to simulate the situation, a total of 9 notifications were sent to the notifications API for each round of tests:

| # | Topic | No. of subscribers |
| - | ---------- | ----------- | 
| 1 | breaking/uk | 1522850 |
| 2 | breaking/us | 544993 |
| 3 | breaking/au | 290658 |
| 4 | breaking/international | 1000158 |
| 5 | breaking/uk-sport | 28763 |
|   | breaking/us-sport | |
|   | breaking/au-sport | |
|   | breaking/international-sport | |
| 6 | tag-series/world/series/guardian-morning-briefing | 265979 |
| 7 | tag-series/membership/series/weekend-reading | 206707 |
| 8 | editions/uk | 143949 |
| 9 | breaking/uk | 3358659 |
|   | breaking/us | |
|   | breaking/au | |
|   | breaking/international | |

We used the figures from the last notification (with 3358659 subscribers) for evaluation.

## Test Results

### Instance class `t4g.medium`
| # | No. harvester invocations | Avg. processing (s) | Total harvester duration (s) | Error "marked as broken" |
| ----------- | ----------- | ----------- | ----------- | ----------- |
| 1	| 335 | 63.61 | 73.29 | 0 |
| 2	| 335 | 28.93 | 39.53 | 0 |
| 3	| 335 | 39.85 | 52.11 | 0 |
| 4	| 335 | 34.35 | 44.64 | 0 |
| AVG | 335| 41.69 | 52.39 | 0 |

### Instance class `t4g.large`
| # | No. harvester invocations | Avg. processing (s) | Total harvester duration (s) | Error "marked as broken" |
| ----------- | ----------- | ----------- | ----------- | ----------- |
| 1	| 335 | 54.26 | 62.26 | 0 |
| 2	| 335 | 57.97 | 65.37 | 0 |
| 3	| 335 | 38.04 | 47.15 | 0 |
| 4	| 335 | 32.36 | 45.83 | 0 |
| AVG | 335 | 45.66 | 55.15 | 0 |

### Instance class `m6g.large`
| # | No. harvester invocations | Avg. processing (s) | Total harvester duration (s) | Error "marked as broken" |
| ----------- | ----------- | ----------- | ----------- | ----------- |
| 1	| 335 | 40.18 | 53.38 | 0 |
| 2	| 335 | 40.34 | 50.03 | 0 |
| 3	| 335 | 38.29 | 43.01 | 0 |
| 4	| 335 | 39.54 | 51.96 | 0 |
| AVG | 335 | 39.59 | 49.60 | 0 |

## Conclusion
From the test results, there are no signitifcant difference on the performance of the harvester between these instance classes.  There may be a slight improvement with `m6g.large`, but the performance is not consistently better and its cost is more than twice as much as the cost of `t4g.medium`.  

Halfway through the tests, I collected the figures of SQL execution times from the `pg_stat_statements` as well (Initially I did not because it seemed to me we did not change the SQL or the storage, and the query execution should be largely in line with the duration of harvesters).  

The results for `m6g.large` are shown below:

| query | min time (ms) | mean time (ms) | max time (ms) |
| ----- | ------------- | -------------- | ------------- |
| SQL for one topic | 12.02 | 22.04 | 96.17 | 
| SQL for four topic | 6.26 | 31.11 | 1757.7 |

The results for `t4g.medium` are shown below:

| query | min time (ms) | mean time (ms) | max time (ms) |
| ----- | ------------- | -------------- | ------------- |
| SQL for one topic | 15.88 | 38.95 | 257.50 |
| SQL for four topic | 7.76 | 38.6 | 2146.58 |

It appears that the `m6g.large` does improve the query execution time.  In addition, the average duration of harvesters is around 40 seconds while the query execution is claimed to last less than 1 second in most cases.  So it would be interesting to see that the faster query exeuction does not have noticeable impact on the duration of the harvester.

I would recommend studying where the harvester spends its time before we work on other database tuning.




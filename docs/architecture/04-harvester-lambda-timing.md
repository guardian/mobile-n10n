
# Harvester lambda timing

When we performed performance tests on the harvester lambda with different RDS instance classes, we noticed that the average execution time of harvesters was reported to be around 30 secnds but the SQL query time was mostly reported to be under 1 second.

So we looked at the timing of harvester lambda.

# Set up

I modified the harvester to write more data to log markers, and sent a `dryRun` notification with ~900,000 recipients on `CODE`.  (01-Sep-2022 11:57 - 11:58)

It had 153 invocations of harvester lambda.  The average processing duration is 30.06s and the total duration is 47.01s.

# Findings

Here are the findings:

1. The average processing time shown in our dashboard is actually the period of time from the message being sent to the SQS and the end of the harvester execution.  Although this figure is around 30s, the actual harvester took much shorted time (less than 1s ~ 11s).  This seems to suggest that the messages were left in the SQS for quite a while before being picked up.

2. There were a total of 153 invocations of harvester, and around 12 invocations require cold start (i.e. starting up from the docker image and running some initialisation). 
- For those with cold start, it took around 4-5s to initialize the lambda runtime and another 5s to exeute the lambda.
- For those without cold start, it took less than 1s to execute the lambda

3. All the 153 messages were sent to SQS within a period of 120 ms.  So it meant all the messages to be processed were already in the queue at the beginning.

4. For the first 18 seconds, there were actually only two instances running in parallel.  Each of them processed messages sequentially.

5. New instances were created over the time.  At 20^th^ second, 30^th^ second and 40^th^ second, we had 7 instances, 11 instances and 12 instances respectively.

I checked the timing of another breaking news notification, which was sent on PROD to 1.9M recipients (05-Sep-2022 12:39 - 12:40).  It had 359 invocations with an average processing duration of 30.02s and total duration of 48.43s.

1. All the 359 messages were sent to SQS within a period of 100 ms.

2. For the first 10 seconds, only two instances were running to process the messages.

3. New instances were created over the time.  At 10^th^ second, 20^th^ second, 30^th^ second and 40^th^ second, we had 3 instances, 8 instances, 14 instances and 18 instances  respectively.

# Conclusion

1. The lambda starts processing the messages from SQS with a relatively smaller number of processes even if there are more than 100 visible messages on the queue.

2. The lambda scales up the number of processes (thus the number of concurrent executions) gradually.

3. According to AWS documentation, at the maximum rate of scaling up, the lambda creates 60 more instances per minute.  In our experiment, we oberved that the lambda starts scaling up at a much lower rate, but the rate may increase over time.

4. It seems to suggest that the lambda may take minutes to scale up to the level we need.  As we want to complete the whole delivery pipeline under 2 minutes, this rate of scaling up is unlikely to meet our requirement.

5. It appears that there is little we can do to make the lambda scale up at a higher rate.  (David did some great work about this [here](../testing/08-provisioned-concurrency.md))

6. It may require some changes to architecture to further reduce the duration of the harvester's execution.

7. As recommended by AWS support, the next step is to increase the batch size of messages for harvestors so that each invocation can process multiple messages.  
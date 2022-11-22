# Monitoring the new EC2 stack

We have been implementing a new EC2 stack for the process of sender function, and plan to run an experiment with this EC2 stack on PROD.

In this study, we are exploring different ways to monitor the health of the new EC2 stack and evaluate its performance after we roll it out on PROD.

## Health

The first goal is to make sure that the new EC2 stack properly picks up and processes SQS messages, which are sent from harvesters.

Cloudwatch alarms can be set up to monitor how well the EC2 sender takes message from SQS by the following SQS metrics

(1) `ApproximateAgeOfOldestMessage`.  This metric is the age of the oldest messages that are available to be picked up.  It will have a large value if the sender function fails to pick up messages from the SQS.

(2) `ApproximateNumberOfMessagesNotVisible`.  This metric is the number of messages that have been read by the sender but have not been deleted.  A high value of this metric may hint that the sender function may keep taking the messages but fail to complete their processing.

We should also make sure that the EC2 stack processes the messages successfully.  At the end of ech sender function invocation, it generates four metric data points: `success`, `failure`, `dryrun`, `total`.  We may be able to create custom metrics out of them for monitoring purpose.

(3) The sender function generates the four metric data points when it finished processing a SQS message.  So we expect to have almost the same number of metric data between the number of messages received by the SQS queue and the metric `total` (or one of the other three).  So a custom metric on the differnece between these two metrics can be used to monitor whether the EC2 stack finishes processing every SQS messages it takes.  

(4) The metric (3) does not differentiate between success and failed processing.  So we can create another metric which compute the failure rate by ```failure / (total - dryrun)```.

## Performance

The second goal is to evaluate the performance.

(5) We can continue to use our 90in2 SLI as a measure of the overall performance.

The use of EC2 stack is mainly designed to tackle the bottleneck due to the low concurrency level of sender function in lambda runtime.  So it may give us insight into its performance by splitting it into two figures:
1. concurrency level, which is how many sender functions are being executed in parallel.
2. processing time taken by each function invocation

(6) For a particular notification ID, if we count the `start` and `end` logs of sender function up to a timestamp and subtract the count of `end` logs from the count of `start` logs, the value should indicate how many function invocation is happening at this particular moment.

(7) The statistics on the processing time can be collected with the existing metric `worker.functionProcessingTime`.

A sample Kibana dashboard was created to demonstrate the idea: https://logs.gutools.co.uk/s/mobile/app/dashboards#/view/75c0d220-6447-11ed-be34-2d379af36700?_g=(filters:!())

## Follow up tasks
1. Change the sender function such that it sends metric to AWS under another namespace if running in EC2

2. Create the alarms and the required metrics as proposed in (1)-(4)

3. Change the sender function to make a log before parsing chunked token event body

4. Create Kibana dashboard for the metrics (6) and (7)




We are exploring ways to monitor the health of the new EC2 stack and evaluate its performance after we roll it out on PROD for experimentation.

## Health

The first goal is to make sure that the new EC2 stack works properly to pick up and process SQS messages from harvesters.

An alarm can be set up to monitor how well the EC2 sender takes message from SQS by the SQS following metrics

(1) `ApproximateAgeOfOldestMessage`.  This metric is the age of the oldest messages that are available to be picked up.  It will have a large value if the sender function fails to pick up messages from the SQS.

(2) `ApproximateNumberOfMessagesNotVisible`.  This metric is the number of messages that have been read by the sender but have not been deleted.  A high value of this metric may imply that the sender function may keep reading the messages but fail to complete their processing.

At the end of ech sender function invocation, it generates four metric data points: `success`, `failure`, `dryrun`, `total`  

(3) We can create a metric to link up the number of messages received by the SQS queue to the number of result logs produced by sender.  It can be used to monitor whether the EC2 sender is able to process SQS messages to the completion.  

(4) The metric (3) does not differentiate between success and failed processing.  So we can create another metric which compute the failure rate by `failure` / (`total` - `dryrun`).

## Performance

The second goal is to evaluate the performance.

(5) We can continue to use our 90in2 SLI as a measure of the overall performance

The use of EC2 stack is mainly designed to tackle the bottleneck due to the low concurrency level of sender function in lambda runtime.  So it may help us understand whether the EC2 stack produces the intended result by monitoring the concurrency level, which is how many SQS messages can be processed in parallel.

(6)

And high concurrency level will not lead to performance gain if EC2 sender takes much longer processing time.

(7) The statistics on the processing time can be collected with the existing metric `worker.functionProcessingTime`.




# RDS Proxy

This document defines the test results for the RDS proxy in the CODE environment.

## Summary

The RDS proxy introduces a slight degradation in performance of the harvester workers. It could be considered as a good mechanism for allowing us to switch out what DB is used in production (minimising downtime). The main benefit would be other performance factors:
- Protecting the DB when managing many connection requests
- Speeding up failover time

| |Average total duration of harvester (ms)|No. harvester invocations|Harvester DB connection errors|
|:----|:----|:----|:----|
|Without RDS proxy|141.5|104|22.6|
|With RDS proxy|149.5|114|32.2|

For now we agreed not to push this change into production.

## Background

We have been receiving database I/O errors in the harvester meaning some users are not receiving notifications every time a notification is sent.

As part of the investigation into why this error is occurring, we realised that the database is receiving too many concurrent calls and can’t handle the amount of requests in a timely manner. As a result the harvester database requests time out (45s timeout).

One of the solutions we found that could improve this was to use RDS proxy between the Harvesters and the database. RDS proxy serves this purpose and can have a positive impact on the performance of the database calls since it organises and manages the connection pools.

Another additional benefit would be to allow switching the backend (origin) databases without significant downtime.


## Test Setup

We set up an RDS proxy for our registrations db in [this](https://github.com/guardian/mobile-n10n/pull/666) PR.

With the RDS proxy infrastructure created in an environment we can test this performance impact by:
- updating the registrations db url in SSM for the worker lambda (+ topicCount, cleaner, if needed) to `jdbc:postgresql://registrations-db-proxy-cdk-code.proxy-crwidilr2ofx.eu-west-1.rds.amazonaws.com/registrationsCODE?currentSchema=registrations`
- redeploying `notificationworkerlambda` to ensure new lambda invocations consume the latest values from SSM.

NB: the previous registrations db url can be found from the parameter history tab in SSM.

We created a copy of the PROD registrations db, with the tokens sanitised (so no notifications can be accidentally sent to real devices).

The registrations db url SSM parameters were updated to point the worker lambda to the RDS proxy.

The worker lambdas were redeployed to ensure the latest SSM values were consumed while testing.


We sent a message to the `/push/topic` endpoint:

```
{
  "id": "8c94a1c8-8949-436a-935e-87efedc19b2f",
  "type": "news",
  "title": "The Guardian",
  "message": "Donald Trump is set to deliver his July 4th speech in a 'salute to America' amid bad weather. Follow live",
  "thumbnailUrl": "https://media.guim.co.uk/0e627795c1edf25d44bfd9472584f607dee51cae/0_219_3291_1975/500.jpg",
  "sender": "dave.earley@guardian.co.uk",
  "link": {
	"contentApiId": "us-news/live/2019/jul/05/donald-trump-delivers-july-4th-speech-live",
	"shortUrl": "https://gu.com/p/bnn3z",
	"git": {
  	"mobileAggregatorPrefix": "item-trimmed"
	}
  },
  "importance": "Minor",
  "topic": [
	{
  	"type": "breaking",
  	"name": "uk"
	}
  ],
  "dryRun": true
}
```

We can query the testRig db to check the number of subscribers to the `breaking/uk` topic:

```
SELECT COUNT(topic), topic FROM registrations.registrations;
```

Topic `breaking/uk` had 1,619,036 subscribers. At the time of testing the worker batch size was 20,000 meaning:
- Shard size ~810
- Expected number of SQS messages to be processed by the worker lambdas ~81

## Full Test Results

For each test run only the harvester app was analysed as this is where we notice the bottleneck in the notification sending process.

### Without RDS Proxy

DB url:	`jdbc:postgresql://notifications-registrations-db-private-testrig.crwidilr2ofx.eu-west-1.rds.amazonaws.com/registrationsCODE?currentSchema=registrations`				

|#|Total harvester duration (ms)|No. harvester invocations|Harvester DB connections opened|Harvester DB connections closed|Harvester error "marked as broken"|
|:----|:----|:----|:----|:----|:----|
|1|130|100|100|49|19|
|2|195|126|126|60|45|
|3|180|119|119|58|38|
|4|140|104|104|81|23|
|5|112|91|91|60|10|
|6|122|85|85|80|4|
|7|151|113|113|70|32|
|8|150|103|103|81|22|
|9|130|101|101|81|20|
|10|105|94|94|81|13|
|AVG|141.5|103.6|103.6|70.1|22.6|

### With RDS Proxy

DB url: `jdbc:postgresql://registrations-db-proxy-cdk-code.proxy-crwidilr2ofx.eu-west-1.rds.amazonaws.com/registrationsCODE?currentSchema=registrations`

|#|Total harvester duration (ms)|No. harvester invocations|Harvester DB connections opened|Harvester DB connections closed|Harvester error "marked as broken"|
|:----|:----|:----|:----|:----|:----|
|1|150|121|121| | |
|2|155|113|113|82| |
|3|141|129|129|82| |
|4|127|96|96|82| |
|5|135|93|92|66|16|
|6|207|129|129|81|48|
|7|165|118|118|81|37|
|8|125|108|108|59|27|
|9|150|115|115|81|34|
|10|140|118|70|55|31|
|AVG|149.5|114|109.1|74.3|32.17|

^ some missing data points as these tests were run first and we were still working out what data would be useful for analysis/comparison.

### Analysis

#### Harvester duration

There's a slight increase in the total average duration of the harvester. The total duration was taken between when the first lambda logged `START RequestId: xyz` and the last lambda logged `END RequestId: zyx`.

The increase in total average duration is likely a side effect of the total number of lambdas executed.

#### Number of harvester lambda invocations and connections "marked as broken"

The minimum number of harvester lambda invocations we would expect to see should be the shard range divided by the shard size. In our tests the shard space is from -32,768 to 32,767 and the shard size is 810. This means the notification app should put 81 SQS messages on the queue (where each SQS message corresponds to a given shard range).

We can see that the number of lambda invocations is never this minimum number. The likely cause is because of errors, meaning the SQS message processing is not successful and is (after a given timeout) visible on the queue for another lambda to process.

There is a strong correlation between the number of lambda invocations and the total duration. We can also see that, in general:

`Number of lambda invocations ~= (minimum expected invocations) + (number of invocations ending with a "marked as broken" error)`

#### Number of DB connections opened

It's not surprising that the number of DB connections opened (should) be the same as the number of lambdas invoked - this is almost always the case as every lambda invoked needs a connection to the database.

#### Number of DB connections closed

We'd (expect?) the number of DB connections closed to equal the number of lambdas invoked. However, testing showed a number of connections ended due to an error, in these cases the connection was not terminated gracefully. Taking into account errors, we can see that connections closed did not always equal the number of expected invocations (81).

### Conclusions

- Research why the "marked as broken" error occurs: reducing this will reduce the number of lambda invocations and therefore the overall time
- Validate if the lambda start-up time can be reduced or if we can force a greater level of lambda concurrency
- Evaluate if there are any other changes that could increase the consistency of the performance
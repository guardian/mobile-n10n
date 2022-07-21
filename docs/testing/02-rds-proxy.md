# RDS Proxy

This document defines the test results for the RDS proxy in the CODE environment.

## Summary

The RDS proxy introduces a slight improvement in performance of the harvester workers, notably by reducing the number of "marked as broken" connection errors meaning fewer SQS messages are replayed.

| |Aggregated duration of harvester (s)|No. harvester invocations|Harvester DB connection errors|
|:----|:----|:----|:----|
|Without RDS Proxy|123.4|168|20.6|
|With RDS Proxy|102.8|168.2|15.6|

As well as improving performance the RDS proxy has other benefits:
- Protecting the DB when managing many connection requests
- Speeding up failover time

We agreed to push this change into production.

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

Topic `breaking/uk` had 1,619,036 subscribers. At the time of testing the worker batch size was 10,000 meaning:
- Shard size ~410
- Expected number of SQS messages to be processed by the worker lambdas ~162

## Full Test Results

For each test run only the harvester app was analysed as this is where we notice the bottleneck in the notification sending process.

### Without RDS Proxy

DB url:	`jdbc:postgresql://notifications-registrations-db-private-testrig.crwidilr2ofx.eu-west-1.rds.amazonaws.com/registrationsCODE?currentSchema=registrations`				

When sending only 1 breaking news notification:

|#|Aggregated harvester duration (s)|No. harvester invocations|Harvester DB connections opened|Harvester DB connections closed|Harvester error "marked as broken"|DB connection errors (hits in kibana)|
|:----|:----|:----|:----|:----|:----|:----|
|1|101|162|162|116|0|0|
|2|88|162|162|110|0|0|
|3|171|194|187|81|52|260|
|4|147|213|213|162|51|255|
|5|110|113|115|82|0|0|
|AVG|123.4|168.8|167.8|110.2|20.6|103|

When sending 3 breaking news notifications at the same time:

|#|Aggregated harvester duration (s)|No. harvester invocations|Harvester DB connections opened|Harvester DB connections closed|Harvester error "marked as broken"|DB connection errors (hits in kibana)|
|:----|:----|:----|:----|:----|:----|:----|
|1|458|655|655|486|169|845|
|2|438|650|650|486|164|820|
|AVG|448|652.5|652.5|486|166.5|832.5|

### With RDS Proxy

DB url: `jdbc:postgresql://registrations-db-proxy-cdk-code.proxy-crwidilr2ofx.eu-west-1.rds.amazonaws.com/registrationsCODE?currentSchema=registrations`

|#|Aggregated harvester duration (s)|No. harvester invocations|Harvester DB connections opened|Harvester DB connections closed|Harvester error "marked as broken"|DB connection errors (hits in kibana)|
|:----|:----|:----|:----|:----|:----|:----|
|1|118|162|162|162|0|0|
|2|68|162|162|110|0|0|
|3|141|193|193|85|78|390|
|4|103|162|162|162|0|0|
|5|84|162|162|119|0|0|
|AVG|102.8|168.2|168.2|127.6|15.6|78|

When sending 3 breaking news notifications at the same time:

|#|Total harvester duration (s)|No. harvester invocations|Harvester DB connections opened|Harvester DB connections closed|Harvester error "marked as broken"|DB connection errors (hits in kibana)|
|:----|:----|:----|:----|:----|:----|:----|
|1|445|647|647|486|161|805|
|2|391|609|609|486|123|615|
|AVG|418|628|628|486|142|710|

### Analysis

#### Harvester duration

With the RDS proxy there is a descrease in the aggregated harvester duration. The total duration was taken between when the first lambda logged `START RequestId: xyz` and the last lambda logged `END RequestId: zyx`.

The decrease in aggregated duration is likely a side effect of the total number of lambdas executed.

#### Number of harvester lambda invocations and connections "marked as broken"

The minimum number of harvester lambda invocations we would expect to see should be the shard range divided by the shard size. In our tests the shard space is from -32,768 to 32,767 and the shard size is 410. This means the notification app should put 162 SQS messages on the queue (where each SQS message corresponds to a given shard range).

We can see that the number of lambda invocations is closer to this minimum number when using the RDS proxy. Without the proxy we see high DB connection errors meaning the SQS message processing is not successful and is (after a given timeout) visible on the queue for another lambda to process.

#### Number of DB connections opened

It's not surprising that the number of DB connections opened (should) be the same as the number of lambdas invoked - this is almost always the case as every lambda invoked needs a connection to the database.

### Conclusions

- Research why the "marked as broken" error occurs: reducing this will reduce the number of lambda invocations and therefore the overall time
- Validate if the lambda start-up time can be reduced or if we can force a greater level of lambda concurrency
- Evaluate if there are any other changes that could increase the consistency of the performance
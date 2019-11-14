# Mobile Notifications

This repository contains a set of services and cloud functions necessary in order to send push notifications to the guardian native apps on both iOS and Android.

It has many uses:
* Breaking news sent by our editorial team
* Follow notifications automatically sent when a new article is published
* Football match status alerts when a goal is scored or the status of a game changes
* Technical notification to trigger background download on editions based apps (aka daily edition)
* One off events such as [election results](https://www.theguardian.com/us-news/2016/nov/03/live-presidential-election-night-results-mobile)

## Project structure

* [Api Models](api-models) - All the models sent and received by the notification API
* [Notification](notification) - A service to send or schedule notifications to devices
* [Registration](registration) - A service to register devices to topics
* [Notification Worker Lambda](notificationworkerlambda) - A set of workers that will spin up and process each individual notification by fetching tokens in the database and sending them to APNs or FCM.
* [Report](report) - A service to read the state of each notification. Drives an [Ophan Dashboard Page](https://dashboard.ophan.co.uk/notifications)
* [Event Consumer](eventconsumer) - A lambda that consumes App sent metrics (fastly -> s3 -> lambda) to enrich reporting. This allows us to measure how many devices received a notification, and when.
* [Schedule Lambda](schedulelambda) - A lambda sending notifications on schedule, based on a plan inserted in dynamo. (polling based)
* [DB Cleaner](db-cleaner) - A lambda that deletes token that haven't been active in 120 days. This has been implemented in Rust as an experiment.
* [Fake Breaking News](fakebreakingnewslambda) - A lambda that periodically sends a fake ("dry run") breaking news in order to spot any potential misconfiguration, technical issue or regression. The results of the dry run are closely monitored and raise an alarm if anything goes wrong.
* [Report Extractor](reportextractor) - A daily lambda to export the metadata of each notification into our datalake.

## Features
* Send push notifications to devices in timely manner (~ 3 minutes to reception on device)
* Monitoring of notifications
* Logging for diagnostics and status of each notification

## Architecture
![Architecture Diagram](Notifications-Architecture.jpg)

## Services

### Notification
This service receives requests to send notifications and plans the work for the workers. It stores the status of the notification in the report database, counts how many devices should receive the notification, and split the work accordingly for the harvester to fetch each token chunks.

The chunk size is around 10,000: Not too big such that a complete failure would be a disaster if left undelivered, not too small such that the harvester can efficiently use its running time (ratio cold-start-time / work-time kept as low as possible). 

### Registration
This service receives registrations from the devices. Upon receiving a registration the service completely replace any record present in the DB for that token.
The registration service also checks what topic are invalid and removes any invalid or out of date topic before inserting them in the DB.
The response sent back to the client contains the filtered list such that the client can update the topics on their storage. Out of date topics include live blogs that aren't live anymore or finished football games.

As a side note, each app is programmed such that it will re-register every two weeks even if no subscription has changed.
This would allow us to recover our registration database in roughly 2 weeks in case of complete failure of our system.
It has also allowed us to migrate our users from one backend to another over the period of two weeks while we keep an eye on relevant metrics.

### Report
This service is pretty simple and exposes the status of all notifications that were sent through our system.
It shows information such as ID, title, author, number of devices that received the notification etc.

## Workers

### Harvester

The responsibility of the harvester is to determine which device should receive a notification. This requires a database access.
The harvester takes ranges as an input and fetch that token range from the database. Each range represents up to about 10,000 tokens.
Each token is then individually sorted depending on what platform it targets. The platform is what's used to decide what SQS queue to put the message on.
Before being sent to the SQS queue, tokens are grouped by packets of 1,000. 

As an implementation note, results are streamed as they come back from the database allowing us to start sending tokens to the workers before the request is completed.

### Senders (iOS and Android)
There are two type of senders so far: iOS and Android. Each of these is also configured twice, once for the live app and once for the edition app. That makes a total of 4 lambdas.
The goal of these senders if to prepare the payload as the mobile app expects it, and send the payload to the notification provider (APNs, FCM) as quickly as possible.

No database is accessed at this stage.
Both providers returns information about individual tokens, such as if a token isn't valid anymore. Invalid tokens are queued for deletion and sent to the Registration Cleaning worker.

### Registration Cleaning worker
This worker pick tokens that have been marked for deletion from an SQS queue, and deletes them from the database

### Topic Counter
This worker counts how many devices there are subscribed to each topics. This is then stored as a flat file on S3.
The counts are useful to help the notification service determine how much work there is to send a notification, and split the work accordingly for the harvester.
This is run periodically and only count topics above 1000 devices.

## Further information
 
### Notification Providers

The notification providers actually deliver the notification to the device

* Apple - [APNS: Apple Push Notification Service](https://developer.apple.com/notifications/)
* Android - [FCM: Firebase Cloud Messenger](https://firebase.google.com/docs/cloud-messaging/) (ex GCM)


### Topic Counter

Part of [Notification Worker Lambda(s)](notificationworkerlambda). Retrieves the topics which have more than 1000 subscribers and stores them to s3. Can be run locally by creating the placing the creating the following file in `.gu/notification-worker.conf`

````registration {
  	db {
  		url="jdbc:postgresql://localhost:5432/registrationsCODE?currentSchema=registrations"
  		user="worker_user"
  		password="<CODE DB PASSWORD"
          maxConnectionPoolSize=1
  	}
  }
  topicCounts {
  	bucket="mobile-notifications-topics"
  	fileName="counts.json"
  }
  
````

Tunnel to the CODE notifications database(See[Mobile Platform](https://github.com/guardian/mobile-platform/))) Run `sbt` set `project notificationworkerlambda` and `run` runs the lambda locally. 

   


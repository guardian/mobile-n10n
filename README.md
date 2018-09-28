# Mobile Notifications

* [Registration](registration) - devices registrations, mapping devices to topics
* [Notification](notification) - send a notification for devices registered to a topic (schedule for newsstand)
* [Report](report) - Reports on notifications. Drives an [Ophan Dashboard](https://dashboard.ophan.co.uk/notifications)
* [Event Consumer](eventconsumer) - lambda consumes App sent metrics (fastly -> s3 -> lambda) to enrich reporting
* [Schedule Lambda](schedulelambda) - lambda requests notifications, persisted in DynamoDB, when scheduled

## Why?

### Capabilities
* swap and migrate notification system
* improved monitoring of notifications
* improved logging for diagnostics
* security (gatekeeper to notification system)
* alter existing topic routing (currently done for newsstand, by sharding)

### Rough Flow
| Transport | Data | Reason |
| ------------- |:-------------| -----|
| App -> mobile-n10n | Token, Topics | Register app to receive notifications for given topics |
| mobile-n10n -> Notifications System | Token, Topics | Proxy registration to appropriate notification system |
| Notifier -> mobile-n10n| Content, Topic | Request notification is sent for topic registrations |
| mobile-n10n -> Notifications System | Content, Topic | Proxy request to each each notification system |
| Notifications System -> Provider| Token, Content | Request notification for each app paired to the content, by topic  |
| Provider -> App | Content | Send Notification to each app |
 
## Need to Know 
 
### Notification Provider

Can send a notification to an app, by the token the app can give us.

* Apple - [APNS: Apple Push Notification Service](https://developer.apple.com/notifications/)
* Android - [GCM: Google Cloud Messenger](https://developers.google.com/cloud-messaging/)  (deprecated)
* Android - [FCM: Firebase Cloud Messenger](https://firebase.google.com/docs/cloud-messaging/) (replacing GCM)

#### Rough Flow

| Transport | Data | Reason |  |
|---|---|---|---|
| App -> App Server | Token | Register app get a notification  |
| Notifier -> App Server | Content | Request notification |
| App Server -> Provider| Token, Content | Request notification for each app |
| Provider -> App | Content | Send Notification to each app |

Works for global breaking news only.
Does not support topics.


### Notification Systems

Pair device registrations to topics. Then notifications can be sent per topic, instead of to all.

* [Azure Notifications Hub](https://azure.microsoft.com/en-gb/services/notification-hubs/)
* [FCM: Firebase Cloud Messenger](https://firebase.google.com/docs/cloud-messaging/)

#### Rough Flow

| Transport | Data | Reason |
| ------------- |:-------------| -----|
| App -> Notifications System | Token, Topics | Register app to receive notifications for given topics |
| Notifier -> Notifications System | Content, Topic | Request notification is sent for topic registrations |
| Notifications System -> Provider| Token, Content | Request notification for each app paired to the content, by topic  |
| Provider -> App | Content | Send Notification to each app |

By integrating Apps directly with the Notification System, you risk vendor lock-in and most control. 



   


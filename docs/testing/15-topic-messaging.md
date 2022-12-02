# Topic Messaging

[Topic messaging](https://firebase.google.com/docs/cloud-messaging/android/topic-messaging) is a feature that Firebase offer for sending notifications to large groups of users.

Unlike our current approach, if using topic messaging Firebase would be responsible for storing all tokens associated with a topic (e.g. "breaking/uk"). 
When we want to send a notification to this topic we make a single API call to firebase who sends the notification to all subscribed users.

This is interesting because it would simplify our infrastructure: the harvester and worker lambdas wouldn't be required. 
However, we would be handing over much more responsibility to Firebase, who would ultimately control more of the delivery process.

Firebase give some guidelines about the maximum throughput to expect when using topic messaging.
They define throughput in queries per second (QPS), where (if my understanding is correct) sending a single token registered against a topic is equivalent to 1 QPS.

This means we could expect to reach 1M users in a maximum of 100s - this would be very similar to the performance that we currently achieve in the android workers.
Note that the QPS stated does not include delivery to devices, it's equivalent to the process we currently employ in the harvester and worker lambdas.

There have been some questions in Firebase forums about the speed of topic fanouts which confirm my understanding on QPS:

[Questions before implementing Firebase Topic Messaging ( Push Notifications )](https://groups.google.com/g/firebase-talk/c/KmaF9GqUJAI)

[Firebase Cloud Messagin:: Notification Delivery Delay](https://groups.google.com/g/firebase-talk/c/CqDqzA7irrg/m/bDLU8AFyAwAJ?utm_medium=email&utm_source=footer)


There is no documentation on the equivalent limits (QPS per project) when sending notifications directly (i.e. when tokens are included in the api payload). 
However, [experimentation and further communication with firebase support](16-batching-sqs-messages-and-api-calls.md) indicate that there does appear to be throttling.

The throttling appears to be taking place at the same order of magnitude of QPS (= tokens/s) when sending notifications directly vs topic messaging. As a result there may not be a performance benefit by using topic messaging.
Having said that, our infrastructure complexity and costs would be greatly reduced, so migrating to topic messaging could still be considered.

We would probably only want to use topic messaging if both platforms (android and iOS) are migrated. It's therefore important to understand exactly how firebase deliver notifications to iOS devices.

For now, it's probably worth understanding:
- how firebase send push notifications to iOS (do they proxy via APNS)?
- can the iOS sender worker code be improved, similar to the [experiment](16-batching-sqs-messages-and-api-calls.md) carried out for android?
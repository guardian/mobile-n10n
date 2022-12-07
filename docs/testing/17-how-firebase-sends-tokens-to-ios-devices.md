# How Firebase Send Tokens to iOS Devices

Given some [recent](16-batching-sqs-messages-and-api-calls.md) performance improvements to sending tokens via Firebase a question was raised about whether we could use Firebase to also send tokens to iOS.
Also, given Firebase support a topic messaging feature that would greatly simplify our infrastructure, it's important to understand how this would be supported on the iOS platform.

Note that Firebase [docs](https://firebase.google.com/docs/cloud-messaging/ios/topic-messaging) explicitly include information about configuring topic messaging for iOS devices.

In summary:

- Firebase proxy via APNS (Apple Push Notification Service) to deliver notifications.
- Firebase can only deliver messages directly to an iOS device if the app is in the foreground.
- For the case of Guardian push notifications we expect users to receive messages even when the app is not in the foreground,
the result being that Firebase would always send our push notifications to iOS devices via APNS.

The reference for this information is [here](https://www.youtube.com/watch?v=A1SDBIViRtE).

If we were to send push notifications to iOS devices using Firebase via APNS,
then possibly that would extend the delivery time for iOS users,
compared to our current implementation that interfaces directly with APNS.

For now, it's suggested that we retain our current focus of tuning the iOS worker lambda.
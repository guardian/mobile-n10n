# Topic Messaging

[Topic messaging](https://firebase.google.com/docs/cloud-messaging/android/topic-messaging) is a feature that Firebase offer for sending notifications to large groups of users.

Unlike our current approach, if using topic messaging Firebase would be responsible for storing all tokens associated with a topic (e.g. "breaking/uk"). 
When we want to send a notification to this topic we make a single API call to firebase who sends the notification to all subscribed users.

This is interesting because it would simplify our infrastructure: the harvester and worker lambdas wouldn't be required. 
However, we would be handing over much more responsibility to Firebase, who would ultimately control more of the delivery process.

Firebase give some guidelines about the maximum throughput to expect when using topic messaging.
They define throughput in queries per second (QPS), where (if my understanding is correct) sending a single token registered against a topic to be equivalent to 1 QPS.

This means we could expect to reach 1M users in a maximum of 1000s, or 16 minutes - this would be worse performance than we currently achieve.

There have been some questions in Firebase forums about the speed of topic fanouts which confirm my understanding on QPS:

[Questions before implementing Firebase Topic Messaging ( Push Notifications )](https://groups.google.com/g/firebase-talk/c/KmaF9GqUJAI)

[Firebase Cloud Messagin:: Notification Delivery Delay](https://groups.google.com/g/firebase-talk/c/CqDqzA7irrg/m/bDLU8AFyAwAJ?utm_medium=email&utm_source=footer)


It appears as if there are no equivalent limits (QPS per project) when sending notifications directly (i.e. tokens are included in the api payload). 
For example, one person posting in the forum claims they can reach 450M users within 30mins, which gives them a token processing rate (==QPS) of 250k tokens/second.

For now, and unless Firebase provide additional information to the contrary, it's suggested we continue with our current approach of using multiple concurrent threads and grouping tokens per API call.
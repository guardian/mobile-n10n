# Background

We have observed that our Android Sender Worker performance is noticeably slower than compared the iOS counterpart. Our current architecture means that every time we send a notification we make an API request to APNS and Firebase, which means one API request per message we want to send. We want to explore the possibility of utilising a new version of the Firebase SDK that allows us to batch send tokens, thus reducing the amount of times we need to send a request to the API. In turn, we hope to improve the overall performance of the android sender worker. The ios sender worker will not change.

# Set up
According to the Firebase documentation, if we use a multicast message, we can send a batch of 500 tokens per invocation. https://firebase.google.com/docs/cloud-messaging/send-message#java_2

Code example:

```
List<String> registrationTokens = Arrays.asList(
    "YOUR_REGISTRATION_TOKEN_1",
    // ...
    "YOUR_REGISTRATION_TOKEN_n"
);

MulticastMessage message = MulticastMessage.builder()
    .putData("score", "850")
    .putData("time", "2:45")
    .addAllTokens(registrationTokens)
    .build();
BatchResponse response = FirebaseMessaging.getInstance().sendMulticast(message);
System.out.println(response.getSuccessCount() + " messages were sent successfully");FirebaseMessagingSnippets.java 
```

# Test
We successfully managed to split the way iOS and Android send notifications. For Android we managed to pass in a list of tokens, and sent them as a batch request, whilst allowing iOS to remain unchanged. 

Testing on CODE, we could successfully deliver notifications to both platforms. 

We also managed to successfully test how to handle errors and exceptions, which meant even though one token in the batch failed, it didn't mean the whole batch was dismissed.

Complexity occurs however when we want to clean up the registration token database, if the token is no longer valid or errors for some reason. In the current form, it would mean we clean up the whole list of tokens, there is no way to decide which token in the list is the one that needs cleaning from the database.

# Conclusion
We have concluded this work would require extensive engineering hours to refactor and re-architect how the registrations cleaner handles a list of tokens. Therefore, this is being parked from an experiment point of view and will become a feature change instead, and prioritised accordingly.

# mobile-n10n
mobile-n10n or "mobile-notification" is our next generation of push-notification services.
This project will focus on connecting directly to cloud providers (Azure, GCM) and act as an interface to it.

## Modules
 * registration: Handles the registration requests received from the device, and redirect them to the correct provider. This is soon to be deprecated as we are moving towards Firebase and device will register directly with Firebase.
 * notification: Handles notification messages and redirects them to correct provider
 * report: Exposes data about past notifications
 * common: The common stuff

## Registration

The Play configuration file must contain:
```
gu.msnotifications.endpointUri=https://servicebus-ns.servicebus.windows.net
gu.msnotifications.hubname=nameOfNotficationHub
gu.msnotifications.sharedKeyName=nameOfSharedKey
gu.msnotifications.sharedKeyValue=sharedKeyActualValue

notifications.auditor.content-notifications=http://content-notification-auditor.elb.amazonaws.com
notifications.auditor.goal-alerts=http://goal-alert-auditor.elb.amazonaws.com
```

### Device registration create or udpate

Requires a deviceId, used for updating topics or device ids.

Returns an updated or created registration.
'topics' array is validated against auditor agents ie. it may contain less elements than in original registration.

```
PUT /registrations/deviceId
Content-Type: application/json

{
  "deviceId": "wnsChannelIdOrProviderSpecificDeviceIdentifier",
  "platform": "windows-mobile",
  "userId": "abcd",
  "topics": [
    {"type": "stuff", "name": "stuff"},
    {"type": "other-stuff", "name": "stuff"}
  ]
}
```

returns

```
{
  "deviceId":"deviceAA",
  "platform":"windows-mobile",
  "userId":"idOfUser",
  "topics": [
      {"type":"football-match","name":"match-in-response"}
  ]
}
```

Or an error code if a failure with body string being the reason

### Send push notifications


```
POST /push/?api-key=api-key
Content-type: application/json

{
    "wnsType": "wns/toast",
    "xml": "<xml...>",
    "topics": [
        {"type": "stuff", "name": "stuff"}
    ]
}
```

Returns no relevant content - could return an error code however.

The topics are matched with AND - this can be easily changed however. Or we could restrict it all to just one topic.

As this is an external API it does need hardening - ie input data needs to be validated.

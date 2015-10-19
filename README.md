# mobile-n10n
mobile-n10n or "mobile-notification" is our next generation of push-notification services.
This project will focus on connecting directly to cloud providers (Azure, GCM) and act as an interface to it.

## Modules
 * registration: Handles the registration requests received from the device, and redirect them to the correct provider
 * common: The common stuff
 * backup: the backup and restore batches

## Registration

The Play configuration file must contain:
```
gu.msnotifications.hubname="abc"
gu.msnotifications.connectionstring="Endpoint=sb://abc-ns.servicebus.windows.net/;SharedAccessKeyName=def;SharedAccessKey=ghi="
```

### Device registration

Returns a registration ID.

```
POST /register/
Content-Type: application/xml

{
  "channelUri": "channel-uri",
  "userId": "abcd",
  "topics": [
    {"type": "stuff", "name": "stuff"}
  ]
}
```

returns

```
{ "registrationId": "def" }
```

Or an error code if a failure with body string being the reason

### Device registration updates

Requires a registration ID, used for updating topics.

Returns a registration ID.

```
POST /update/def/
Content-Type: application/xml

{
  "channelUri": "channel-uri",
  "userId": "abcd",
  "topics": [
    {"type": "stuff", "name": "stuff"},
    {"type": "other-stuff", "name": "stuff"}
  ]
}
```

returns

```
{ "registrationId": "def" }
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

### Restoring a backup
Pull the project then run

    sbt "project backup" "set mainClass in (Compile, run) := Some(\"RestoreBoot\")" "run"

The batch is interactive and will prompt you which backup you would like to restore
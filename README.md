# mobile-n10n

Next generation of notifications

## azure

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
POST /push/
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

The topics are matched with OR.

## Authors
* William Narmontas
* Alexandre Dufournet

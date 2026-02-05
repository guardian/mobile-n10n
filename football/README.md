# Mobile Goal Notifications

Service that watches current football matches on PA, and when a goal is scored
uses the Guardian Mobile Notifications service to send a push notification to
subscribed users.

### Testing in CODE

In order to facilitate testing when there are no matches in progress, you can [force](https://github.com/guardian/mobile-n10n/blob/682289cc9651f4f8475c9efc5eb2a3da35985c15/football/src/main/scala/com/gu/mobile/notifications/football/Lambda.scala#L66-L76) the `CODE` environment to run in the past using [`football-time-machine`](https://github.com/guardian/football-time-machine).

1. Find a match which [recently took place](https://www.theguardian.com/football/results).
1. Install a debug version of the app and start following a relevant team or match.
1. Check that the scheduled rule which triggers the Lambda on a regular basis is enabled in `CODE`.
1. Set a date and time for just before the match started. For example, if you want to trigger notifications for a match which kicked off at 8pm on 4th February 2026, you would use: `curl "https://hdjq4n85yi.execute-api.eu-west-1.amazonaws.com/Prod/setDate?startDate=2026-02-04T19:59:00Z"`.
1. Check the [logs](https://logs.gutools.co.uk/s/mobile/goto/cc75dcf9f10d33851bc1bc1e851f96be) to track progress of the match.
1. Your device should receive notifications when significant match events occur (e.g. kick off, goals, half-time etc.)

Note that the Lambda keeps track of which PA events it has processed to avoid sending duplicate notifications, so you will not be able to 'replay' the notifications for the same match more than once.

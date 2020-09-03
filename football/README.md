# Mobile Goal Notifications

Service that watches current football matches on PA, and when a goal is scored
uses the Guardian Mobile Notifications service to send a push notification to
subscribed users.

### Testing in CODE

In order to facilitate testing when there are no matches in progress, you can force the `CODE` environment to run in the past using [`football-time-machine`](https://github.com/guardian/football-time-machine).

1. Find a match which [recently took place](https://www.theguardian.com/football/results).
1. Install a debug version of the app and start following a relevant team or match.
1. Set a date and time for just before the match started (n.b. the [ZonedDateTime](https://docs.oracle.com/javase/8/docs/api/java/time/ZonedDateTime.html) used must be [URL encoded](https://www.urlencoder.org/)) e.g. `curl https://hdjq4n85yi.execute-api.eu-west-1.amazonaws.com/Prod/setDate?startDate=2020-07-26T14%3A59%3A00%2B01%3A00%5BEurope%2FLondon%5D`
1. Check the [logs](https://logs.gutools.co.uk/s/mobile/goto/cc75dcf9f10d33851bc1bc1e851f96be) to track progress of the match.
1. Your device should receive notifications when significant match events occur (e.g. kick off, goals, half-time etc.)



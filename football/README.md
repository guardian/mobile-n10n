# Mobile Goal Notifications (Push and Broadcast)

Service that watches current football matches on PA, and when a goal is scored
uses the Guardian Mobile Notifications service to send a push notification or Live Activity Service to send a broadcast update to
subscribed users.

### Testing in CODE

In order to facilitate testing when there are no matches in progress, you can [force](https://github.com/guardian/mobile-n10n/blob/682289cc9651f4f8475c9efc5eb2a3da35985c15/football/src/main/scala/com/gu/mobile/notifications/football/Lambda.scala#L66-L76) the `CODE` environment to run in the past using [`football-time-machine`](https://github.com/guardian/football-time-machine).

1. Find a match which [recently took place](https://www.theguardian.com/football/results) - football time machine holds data for the last 3 months. To find kick off time, visit the match info tab and add `.json?dcr` to the url - id and kick off will be under `matchInfo`
1. Install a debug version of the app and start following a relevant team or match - to receive push notifications, the debug app must be installed on a device directly from Xcode. Live activities work on the simulator. 
1. Check that the scheduled rule which triggers the Lambda on a regular basis is enabled in `CODE`.
1. Set a date and time for just before the match started. For example, if you want to trigger notifications for a match which kicked off at 8pm on 4th February 2026, you would use: `curl "https://hdjq4n85yi.execute-api.eu-west-1.amazonaws.com/Prod/setDate?startDate=2026-02-04T19:59:00Z"`. 
   - Note: for BST subtract one hour from the local time to get UTC time.
   - Note: for live activities, jump to 30min before kick off to ensure a channel is created, and you receive the initial "start-live-activity" push notification 20min before kick off. 
1. Your device should receive notifications when significant match events occur (e.g. kick off, goals, half-time etc.)
2. Check the [logs](https://logs.gutools.co.uk/s/mobile/goto/cc75dcf9f10d33851bc1bc1e851f96be) to track progress of the match.


**Important:** When you are done testing, please _disable_ the scheduled rule in CODE to stop polling time machine events unneccessarily. The Lambda keeps track of which PA events it has processed (to avoid sending duplicate notifications), so you will not be able to 'replay' the notifications for the same match more than once, without manually deleting the sent notifications by UUID from Dyanmo tables:

- `mobile-notifications-football-notifications-CODE` and `mobile-notifications-reports-CODE` for push notifications tables which can be cumbersome. 
- `mobile-notifications-liveactivities-payload-CODE` for live activities

You can search logs for "Distinct event" to help identify UUIDs of sent notifications.

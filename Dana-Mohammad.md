# How to fetch stats about a notification

While we implement a fully productionised version of the stats, you can use the following instructions to calculate how many notifications where delivered, by platform and by provider.

1. Clone the [n10n repository](https://github.com/guardian/mobile-n10n), checkout the `stats-dana-mohammad` branch
2. Get a Janus token for the [mobile account](https://janus.gutools.co.uk/console?permissionId=mobile-dev&tzOffset=1)
3. Use [this API](https://report.notifications.guardianapis.com/notifications?type=news&api-key=) with a valid API key (ask mobile server side) to find the UUID of a notification you'd like to inspect
4. Run `sbt "project eventconsumer" "run 2018-08-21 976ce265-834f-4ca0-bddd-44510174026e"` by replacing the date and the id with the values you'd like
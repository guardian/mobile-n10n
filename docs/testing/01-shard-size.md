# Shard size

This document defines the test results for doubling the shard size in the CODE environment.

## Summary

Doubling the shard size introduced a significant degradation in performance, both in terms of overall send time and the number of errors/retried messages.

The concept is sound though (i.e. reducing number of lambda executions to reduce overall time) so maybe there could be other changes we make in parallel to this to mitigate the failures.

| |Aggregated duration of harvester (ms)|No. harvester invocations|Harvester DB connection errors|
|:----|:----|:----|:----|
|Current shard size|123.4|168|20.6|
|Doubled shard size|141.5|104|22.6|

For now we agreed not to push this change into production.
# Database tuning - reducing table size

This document summarises the test result and conclusion for reducing table size.  The corresponding background and research work can be found in [the document](../architecture/02-database-tuning.md).

## Results

A performance test was performed for each of the scenarios:
1. Before changes
2. After index rebuilding
3. After full vacuum

Under each scenario, I sent five rounds of breaking news notification.

### Before Changes

| # | Total harvester duration (s) | No. harvester invocations | Harvester error "marked as broken" |
| ----------- | ----------- | ----------- | ----------- |
| 1	| 120.57 | 161 | 0 |
| 2	| 100.03 | 161 | 0 |
| 3	| 106.8 | 161 | 0 |
| 4	| 97.74 | 161 | 0 |
| 5	| 113.26 | 161 | 0 |
| AVG | 107.68 | 161 | 0 |

### After index rebuilding

This operation took around one minute.

```REINDEX INDEX registrations.idx_registration_shard_topic;```

| # | Total harvester duration (s) | No. harvester invocations | Harvester error "marked as broken" |
| ----------- | ----------- | ----------- | ----------- |
| 1 | 49.99 | 161 | 0 |
| 2 | 46.79 | 161 | 0 |
| 3 | 15.02 | 161 | 0 |
| 4 | 26.01 | 161 | 0 |
| 5 | 15.74 | 161 | 0 |
| AVG | 30.71 | 161 | 0 |

### After full vacuum

This operation took around three minutes.

```VACUUM (VERBOSE, ANALYZE, FULL) registrations.registrations;```

| # | Total harvester duration (s) | No. harvester invocations | Harvester error "marked as broken" |
| ----------- | ----------- | ----------- | ----------- |
1 | 33.41| 161 | 0 |
2 | 14.52| 161 | 0 |
3 | 17.94| 161 | 0 |
4 | 24.6 | 161 | 0 |
5 | 15.38| 161 | 0 |
| AVG | 21.17 | 161 | 0 |

## Conclusion
1. The test result supports our hypothesis that reducing table/index size leads to better performance
2. The average harvester duration was greatly reduced from 108s to 31s after index rebuilding
3. The average harvester duration was further reduced to 21s after full vacuum.
4. Reindexing alone results in significant speedup although it just cut down the index size.  One hypothesis is that reduced index size allows the database to make better use of its memory to cache data.
5. It should be noted that the table/index will grow over time so maintenance operations are required on a regular basis.






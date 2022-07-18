# PostgreSQL Query Analyser

The query analyser allows us to monitor how the postgres db queries and indexes are performing.

The `pg_stat_statements` module provides a means for tracking execution statistics of all SQL statements executed by a server.

The module must be loaded by adding `pg_stat_statements` to [shared_preload_libraries](https://www.postgresql.org/docs/10/runtime-config-client.html#GUC-SHARED-PRELOAD-LIBRARIES) in `postgresql.conf`, because it requires additional shared memory. This means that a server restart is needed to add or remove the module.

When `pg_stat_statements` is loaded, it tracks statistics across all databases of the server. To access and manipulate these statistics, the module provides a view, `pg_stat_statements`, and the utility functions `pg_stat_statements_reset` and `pg_stat_statements`. These are not available globally but can be enabled for a specific database with `CREATE EXTENSION pg_stat_statements`.

To use the query analyser we can use the following SQL statements:

To view the stats:

```
SELECT 
  query, calls, total_time / 1000 / 60 AS total_in_min, 
  min_time, max_time, mean_time, stddev_time, rows
FROM pg_stat_statements 
WHERE dbid = 16395
ORDER BY calls DESC;
```
Where `16395` is the ID of the registrationsCODE in the test rig database

To reset the stats:
```
SELECT pg_stat_statements_reset()
```
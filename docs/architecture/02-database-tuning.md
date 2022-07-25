
# Database tuning

We are exploring ways to improve the performance of the registrations database (in terms of the queries executed by harvesters). 

I have looked at the following two areas:

The overall size of the registrations table
The access path of the query (i.e. query plan) 

## Overall size of registrations table

The idea is that it takes less I/O (and thus shorter time) to fetch tables and index from the database if the overall size of these database objects is cut down.  It also allows the database to make better use of its memory for caching.

### Context
When a SQL statement deletes a record from the table, the database does not delete it physically from the table storage.  The record may still be needed.  It is visible to SQL queries which have started before the record deletion and have been working on this particular snapshot of the database.

Similarly, when a SQL statement updates a record, it retains the old copy and physically creates a new row for the updated record.  The old copy is visible to queries that have started before record update while the new record is visible to queries that start afterwards.

Over time, the table gets *bloated* in size with data rows (called **dead rows**) that are from earlier snapshots.  The same mechanism applies to indexes as well.

### Maintenance
There are three operations to deal with the bloated size.

- Autovacuum (standard vacuum)
- Full vacuum
- Reindexing

#### Autovacuum (standard vacuum)
Autovacuum is enabled (which is the default settings) for the registrations database.  It regularly cleans up the dead rows (rows that are older than the configurable “vacuum_freeze_min_age” parameter, expressed in terms of the number of transactions).  It reclaims the space by marking it as reusable, but it does not “defragment” the table or return the space to the system.  It can run in parallel with normal database queries.

#### Full vacuum
Full vacuum is a variant of vacuum operation.  It basically rewrites the whole table to new pages without any dead rows or any unused space in between data rows.  So it can reclaim as much space as possible, return it to the system and minimise the overall storage size for the table.  However, it requires exclusive lock on the table and during this operation, the database is not able to access it. 

#### Reindexing
Reindexing work on indexes only.  It cut down the index size by rebuilding an index.  It can be done by a single command that may result in a period where the index cannot be accessed.  Alternatively, it can be done through a sequence of individual creation and replacement steps which may avoid downtime

### Experiment
I experimented with these operations on the test rig database.  

The following shows the original table size.

```sql
registrationsCODE=> SELECT c.oid, relname AS table_name, c.reltuples,
registrationsCODE->        pg_size_pretty(pg_relation_size(c.oid)) AS table_size,
registrationsCODE->        pg_size_pretty(pg_total_relation_size(c.oid)) AS total_size,
registrationsCODE->        pg_size_pretty(pg_indexes_size(c.oid)) AS index_size,
registrationsCODE->        pg_size_pretty(pg_total_relation_size(reltoastrelid)) AS toast_size
registrationsCODE-> FROM pg_class c
registrationsCODE-> WHERE relnamespace =  16406;
  oid  |            table_name            |  reltuples  | table_size | total_size | index_size | toast_size
-------+----------------------------------+-------------+------------+------------+------------+------------
 16413 | registrations_pkey               |  7.6064e+06 | 3529 MB    | 3529 MB    | 0 bytes    |
 16415 | idx_registration_shard_topic     |  7.6064e+06 | 1190 MB    | 1191 MB    | 0 bytes    |
 16407 | registrations                    | 7.61831e+06 | 2151 MB    | 6872 MB    | 4720 MB    | 8192 bytes
```

I first executed the standard vacuum operation, and the table size did not change noticeably.  It was expected as this operation did not compact the table.

```sql
registrationsCODE=> VACUUM (VERBOSE, ANALYZE) registrations.registrations;
VACUUM

registrationsCODE=> SELECT c.oid, relname AS table_name, c.reltuples,
                           pg_size_pretty(pg_relation_size(c.oid)) AS table_size,
                           pg_size_pretty(pg_total_relation_size(c.oid)) AS total_size,
                           pg_size_pretty(pg_indexes_size(c.oid)) AS index_size,
                           pg_size_pretty(pg_total_relation_size(reltoastrelid)) AS toast_size
                    FROM pg_class c
                    WHERE relnamespace =  16406;

  oid  |            table_name            |  reltuples  | table_size | total_size | index_size | toast_size
-------+----------------------------------+-------------+------------+------------+------------+------------
 16413 | registrations_pkey               | 7.46921e+06 | 3529 MB    | 3529 MB    | 0 bytes    |
 16415 | idx_registration_shard_topic     | 7.46921e+06 | 1190 MB    | 1191 MB    | 0 bytes    |
 16407 | registrations                    | 7.48322e+06 | 2151 MB    | 6872 MB    | 4720 MB    | 8192 bytes
```

Next I executed the reindexing operation on the index *idx_registration_shard_topic*.  Its size was reduced from 1190MB to 355MB.

```sql
registrationsCODE=> REINDEX INDEX registrations.idx_registration_shard_topic;
REINDEX
Time: 54545.778 ms (00:54.546)

  oid  |            table_name            |  reltuples  | table_size | total_size | index_size | toast_size
-------+----------------------------------+-------------+------------+------------+------------+------------
 16415 | idx_registration_shard_topic     | 7.46921e+06 | 355 MB     | 355 MB     | 0 bytes    |
 16413 | registrations_pkey               |  7.6064e+06 | 3529 MB    | 3529 MB    | 0 bytes    |
 16407 | registrations                    | 7.46921e+06 | 2151 MB    | 6036 MB    | 3884 MB    | 8192 bytes
```

Finally I executed the full vacuum operation, which actually covered reindexing.  The table and its indexes were substantially reduced in size from 6872MB (original) to 1750MB.

```sql
registrationsCODE=> VACUUM (VERBOSE, ANALYZE, FULL) registrations.registrations;
VACUUM

  oid  |            table_name            |  reltuples  | table_size | total_size | index_size | toast_size
-------+----------------------------------+-------------+------------+------------+------------+------------
 16407 | registrations                    | 7.46806e+06 | 793 MB     | 1750 MB    | 957 MB     | 8192 bytes
 16413 | registrations_pkey               | 7.46744e+06 | 602 MB     | 602 MB     | 0 bytes    |
 16415 | idx_registration_shard_topic     | 7.46744e+06 | 355 MB     | 355 MB     | 0 bytes    |
```

We ran performance tests before and after reindexing and full vacuum, and the test result were presented in this [document](../testing/05-database-tuning.md).

## The access path of the query
The access path can tell us how the database reads and process database objects to retrieve the final result set.  

The most time-critical query in the notification delivery pipeline is the query made by the harvester to fetch device tokens based on the topic and shard range.

According to the query plan, the database takes three steps to execute the query:

| Step | Description |
| ----------- | ----------- |
| 1 | Bitmap index scan on *idx_registration_shard_topic* (cost 4468) |
|  | produce a bitmap which tells the next step which pages to fetch (so it can skip pages which does not contain the row it wants) |
| 2 | Bitmap heap scan on *registrations* table (cost 35000) |
|  | read the relevant pages and fetch the data row that satisfies its *WHERE* clause |
| 3 | HashAggregate (cost 100) |
|  | remove duplicated rows (those having the same *token*, *platform* and *buildtier*) |

This query plan is sensible.  The index *idx_registration_shard_topic* is scanned first to find out which pages contain the records that meet the *WHERE* clause.  These pages are then scanned to select these records.  (A page is the basic unit used by the database to do I/O.  It reads the whole page even if it just need a bit of data on it).

So, most of the time is spent the second step.  It is possible that it reads a lot of pages but only a small part of the data really satisfies the *WHERE* clause because the physical locations of data rows have nothing to do with their content.  Data rows of the same topic and the shard range is likely to be scattered throughout the pages.

### Materialized view
One option I have considered is to use materialized views.

A materialized view differs from a view in that a materialized view persists the result set of its query in the storage upon creation.  So it returns the result set without executing the query again, but it requires manual **REFRESH** to update the result set.

One idea is to prepare a number of materialized views to cluster related data rows (from the same topic and the same range of shard) so that we can query the much smaller materialized views instead of the whole table.  However it has two major drawbacks.

1. It requires manual refresh to update each of the materialized views
2. Harvester should be changed to access the correct materialized view based on the topic and shard range.  The program logic would be complicated and tightly coupled with the way we break down the whole table records into several record sets.  

It appears that table partitioning may be a better option.

### Table partitioning

As the harvesters mainly fetch data rows from a topic within a contiguous shard range, it may be good if we could cluster these *related* records in adjacent pages.  We may be able to do this by table partitioning.

Postgresql 10 only provides limited support on table partitioning.  We may explore this approach more after we perform Postgresql 14 upgrade.

Before that, I did some simple tests on this concept by dividing the *registrations* table into 8 partitions based on their *shard* values.

```sql
CREATE TABLE registrations.registrations_partitioned
(
    token text COLLATE pg_catalog."default" NOT NULL,
    topic text COLLATE pg_catalog."default" NOT NULL,
    platform text COLLATE pg_catalog."default" NOT NULL,
    shard smallint NOT NULL,
    lastmodified timestamp with time zone NOT NULL,
    buildtier text COLLATE pg_catalog."default"
) PARTITION BY RANGE (shard);

CREATE TABLE registrations.registrations_partitioned_shard1 PARTITION OF registrations.registrations_partitioned
    FOR VALUES FROM (-32768) TO (-24576);
CREATE TABLE registrations.registrations_partitioned_shard2 PARTITION OF registrations.registrations_partitioned
    FOR VALUES FROM (-24576) TO (-16384);
CREATE TABLE registrations.registrations_partitioned_shard3 PARTITION OF registrations.registrations_partitioned
    FOR VALUES FROM (-16384) TO (-8192);
CREATE TABLE registrations.registrations_partitioned_shard4 PARTITION OF registrations.registrations_partitioned
    FOR VALUES FROM (-8192) TO (0);
CREATE TABLE registrations.registrations_partitioned_shard5 PARTITION OF registrations.registrations_partitioned
    FOR VALUES FROM (0) TO (8191);
CREATE TABLE registrations.registrations_partitioned_shard6 PARTITION OF registrations.registrations_partitioned
    FOR VALUES FROM (8191) TO (16384);
CREATE TABLE registrations.registrations_partitioned_shard7 PARTITION OF registrations.registrations_partitioned
    FOR VALUES FROM (16384) TO (24576);
CREATE TABLE registrations.registrations_partitioned_shard8 PARTITION OF registrations.registrations_partitioned
    FOR VALUES FROM (24576) TO (MAXVALUE);

CREATE INDEX idx_regpart_shard1_topic
    ON registrations.registrations_partitioned_shard1 USING btree
    (shard ASC NULLS LAST, topic COLLATE pg_catalog."default" ASC NULLS LAST);
CREATE INDEX idx_regpart_shard2_topic
    ON registrations.registrations_partitioned_shard2 USING btree
    (shard ASC NULLS LAST, topic COLLATE pg_catalog."default" ASC NULLS LAST);
CREATE INDEX idx_regpart_shard3_topic
    ON registrations.registrations_partitioned_shard3 USING btree
    (shard ASC NULLS LAST, topic COLLATE pg_catalog."default" ASC NULLS LAST);
CREATE INDEX idx_regpart_shard4_topic
    ON registrations.registrations_partitioned_shard4 USING btree
    (shard ASC NULLS LAST, topic COLLATE pg_catalog."default" ASC NULLS LAST);
CREATE INDEX idx_regpart_shard5_topic
    ON registrations.registrations_partitioned_shard5 USING btree
    (shard ASC NULLS LAST, topic COLLATE pg_catalog."default" ASC NULLS LAST);
CREATE INDEX idx_regpart_shard6_topic
    ON registrations.registrations_partitioned_shard6 USING btree
    (shard ASC NULLS LAST, topic COLLATE pg_catalog."default" ASC NULLS LAST);
CREATE INDEX idx_regpart_shard7_topic
    ON registrations.registrations_partitioned_shard7 USING btree
    (shard ASC NULLS LAST, topic COLLATE pg_catalog."default" ASC NULLS LAST);
CREATE INDEX idx_regpart_shard8_topic
    ON registrations.registrations_partitioned_shard8 USING btree
    (shard ASC NULLS LAST, topic COLLATE pg_catalog."default" ASC NULLS LAST);
```

The following shows the query plan on the `registrations` table (after full vacuum operation):

```sql
registrationsCODE=> EXPLAIN SELECT token, platform, buildTier FROM registrations.registrations
registrationsCODE-> WHERE (topic IN ('breaking/uk') ) AND ((shard >= -25967 ) AND (shard <= -25568 ))
registrationsCODE-> GROUP BY token, platform, buildTier;
                                                         QUERY PLAN
-----------------------------------------------------------------------------------------------------------------------------
 HashAggregate  (cost=30809.81..30907.75 rows=9794 width=45)
   Group Key: token, platform, buildtier
   ->  Bitmap Heap Scan on registrations  (cost=1690.53..30736.35 rows=9794 width=45)
         Recheck Cond: ((shard >= '-25967'::integer) AND (shard <= '-25568'::integer) AND (topic = 'breaking/uk'::text))
         ->  Bitmap Index Scan on idx_registration_shard_topic  (cost=0.00..1688.08 rows=9794 width=0)
               Index Cond: ((shard >= '-25967'::integer) AND (shard <= '-25568'::integer) AND (topic = 'breaking/uk'::text))
(6 rows)
```

The following shows the query plan on the partitioned `registrations_partitioned` table.  The estimated cost is reduced from 30907 to 19410. 

```sql
registrationsCODE=> EXPLAIN SELECT token, platform, buildTier FROM registrations.registrations_partitioned 
WHERE (topic IN ('breaking/uk') ) AND ((shard >= -25967 ) AND (shard <= -25568 )) 
GROUP BY token, platform, buildTier;
                                                                 QUERY PLAN
--------------------------------------------------------------------------------------------------------------------------------------------
 HashAggregate  (cost=19410.42..19420.18 rows=976 width=122)
   Group Key: registrations_partitioned_shard1.token, registrations_partitioned_shard1.platform, registrations_partitioned_shard1.buildtier
   ->  Append  (cost=1684.88..19337.23 rows=9759 width=122)
         ->  Bitmap Heap Scan on registrations_partitioned_shard1  (cost=1684.88..19337.23 rows=9759 width=122)
               Recheck Cond: ((shard >= '-25967'::integer) AND (shard <= '-25568'::integer) AND (topic = 'breaking/uk'::text))
               ->  Bitmap Index Scan on idx_regpart_shard1_topic  (cost=0.00..1682.44 rows=9759 width=0)
                     Index Cond: ((shard >= '-25967'::integer) AND (shard <= '-25568'::integer) AND (topic = 'breaking/uk'::text))
(7 rows)
```

It suggests that table partitoning may be a viable option.

## Next step
- [X] Run performance tests - [result](../testing/05-database-tuning.md)
- [X] Apply full vacuum to PROD
- [ ] We may devise the index rebuilding step that could minimize impact on the READ performance during its operation, and apply it as a scheduled job.
- [ ] We may explore partitioned table after Postgresql 14 upgrade.






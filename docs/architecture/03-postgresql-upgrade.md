
# Registrations Database Upgrade

We are working to improve the performance of our notification delivery pipeline.  We are going to upgrade the registrations database from Postgresql 10 to its higher major version because of the following reasons:

1. Improvement to existing features in newer versions of Postgresql
2. Support for new features which may be useful to our work
3. Gravitron2 processor (instance class t4g) is supported for Postgresql 12 or above.  The processor delivers up to [40% better price performance](https://aws.amazon.com/about-aws/whats-new/2020/09/announcing-new-amazon-ec2-t4g-instances-powered-by-aws-graviton2-processors/) over T3 instances.
4. The PostgreSQL community will [discontinue support for PostgreSQL 10 on November 10 2022](https://www.postgresql.org/support/versioning/), and will no longer provide bug fixes or security patches for this version.

## Postgresql version we upgrade to

I list out the major enhancements in each of major versions which may be relevant to registrations database as below

### Postgresql 11
1. Better support for table partitioning (which may be a good way to optimize our database)
- Add support for primary key on partitioned tables
- Allow *default* partition
- Allow update on partition key columns
- Enhance partition elimination strategies

Reference: https://www.postgresql.org/docs/11/release.html

### Postgresql 12
1. Optimizations to space utilization and read/write performance for B-tree indexes
2. Improve performance of many operations on partitioned tables
3. REINDEX CONCURRENTLY can rebuild an index without blocking writes to its table

Reference: https://www.postgresql.org/docs/12/release.html

### Postgresql 13
1. Space savings and performance gains from de-duplication of B-tree index entries
- Expect a considerable amount of duplicated values of (topic, shard) in the index
2. Improved performance for queries that use partitioned tables
- Allow pruning of partitions to happen in more cases
- Allow partitioned tables in logical replication
3. Minor: Implement incremental sorting

Reference: https://www.postgresql.org/docs/13/release.html

### Postgresql 14
1. Improve the performance of updates and deletes on partitioned tables with many partitions
2. Reduce index bloat on tables whose indexed columns are frequently updated.
3. Improve vacuum operations in reclaiming space

Reference: https://www.postgresql.org/docs/14/release.html

## RDS Proxy and Progresql upgrade
The latest major version of Postgreql which RDS proxy supports is [version 13](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-proxy.html#rds-proxy.support).  So we have two options:

1. Upgrade to Postgresql 14 without RDS proxy 
2. Upgrade to Postgresql 13 with RDS proxy

The major enhancements of postgresl 14 which are relevant to our system are feature improvement but they are not critical to our DB tuning work.  Moreover, our previous performance test suggests that RDS proxy can help with the performance, probably because it manages a lot of short-lived database connections from harvester functions and move the workload away from the database instance.  So I recommend upgrading to Postgresql 13.  We always have the option of upgrading again from Postgresql 13 to 14.

At this moment, if we use AWS in-place upgrade, the registrations database (version 10.18) can be upgraded to [version 13.4](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_UpgradeDBInstance.PostgreSQL.html#USER_UpgradeDBInstance.PostgreSQL.MajorVersion).  The database will be automatically upgraded to the latest "automatic upgrade version", which is version 13.6 at the moment, during maintenance window.

## Performance test with Postgresql 13
The test result can be found in this [document](../testing/06-postgresql-upgrade.md).  Apparently it does not have direct impact on the performance.  However, Postgresql 13 improves the way the index is maintained and the way it does the vacuum and index rebuilding operations, which are useful to our database in the long run.  In addition, it provides a much better support in table partitioning which potentailly helps us to fine tune the registrations table further.

## Upgrade procedure
In addition to upgrading the database engine, we are going to execute the following operations:
1. Back up existing PROD
2. Change the instance class from `t3` to `t4g`
3. Re-generate optimizer statistics (step 14 in [user guide](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_UpgradeDBInstance.PostgreSQL.html#USER_UpgradeDBInstance.PostgreSQL.MajorVersion.Process)) as the upgrade process does not transfer the optimizer statistics
4. Re-indexing so that the existing indexes can use new features provided by the upgraded database engine (for example, check E.8.3.1.2. Indexes in this [release note](https://www.postgresql.org/docs/13/release-13.html#id-1.11.6.12.5))

I came up with the following options to perform the major version ugprade.

### Option 1: Use AWS RDS in-place upgrade

We just go ahead with modifying the DB engine via AWS console.

```mermaid
gantt
    title Approach 1 - In-place upgrade
    dateFormat  HH:mm:ss
    axisFormat  %H:%M:%S
    section Operation
    Upgrade to postgres13             :crit,active, a1, 00:00:00, 30m
    Change instance class             :crit,active, a2, after a1, 20m
    Reindexing and optimizer stats    :crit,active, a3, after a2, 10m
    Service restored                  :milestone, after a3, 0m
    section Impact 
    No registration                   :crit, c1a, 00:00:00, 60m
    No notification                   :crit, c1b, 00:00:00, 60m
```

Rollback:
1. Restore the latest snapshot

Advantages:
1. Simplest procedure

Disadvantages:
1. Service downtime throughout the upgrade


### Option 2: Create a new instance for Postgres13 database

We create a new instance from the latest snapshot from registrations database and make changes to it.

```mermaid
gantt
    title Approach 2 - Create new Postgres13 database
    dateFormat  HH:mm:ss
    axisFormat  %H:%M:%S
    section PROD DB
    Backup                            :a1, 00:00:00, 5m
    section PG13 DB
    Restore                           :b1, after a1, 20m
    Upgrade to postgres13             :crit,active, b2, after b1, 30m
    Change instance class             :crit,active, b3, after b2, 20m
    Reindexing / optimizer stats      :crit,active, b4, after b3, 10m
    Switchover                        :b5, after b4, 5m
    Serving notifications             :milestone, after b5, 0m
    section Impact 
    Registration lost       :crit, c1a, 00:00:00, 90m
    Notification available  :done, c1b, 00:00:00, 90m
    Normal                  :done, c2, after c1a c1b, 5m
```

Rollback:
1. Before switchover, no action is needed
2. After switchover, we have to switch back to roll back

Advantages:
1. No downtime to breaking news notification

Disadvantages:
1. Changes to registrations database that happen during upgrade will be lost after switchover
2. Readers may find our system unreliable (subscribing to a topic but later find that it is not on our database)

### Option 3: Maintain breaking news notification service in temporary database

We create a tempoarary instance from the latest snapshot from registrations database and serve the breaking news notifications there.

```mermaid
gantt
    title Approach 3 - Create temporary database to serve breaking news
    dateFormat  HH:mm:ss
    axisFormat  %H:%M:%S
    section PROD DB
    Backup                            :a1, 00:00:00, 5m
    Upgrade to postgres13             :crit,active, a2, after b2, 30m
    Change instance class             :crit,active, a3, after a2, 20m
    Reindexing / optimizer stats      :crit,active, a4, after a3, 10m
    Switch back                       :a5, after a4, 5m
    Serving all                       :milestone, after a5, 0m
    section Temp DB
    Restore                           :b1, after a1, 20m
    Switchover lambda workers         :b2, after b1, 5m
    Serving harvester                 :milestone, after b2, 0m
    section Impact 
    Normal                  :done, c1, 00:00:00, 30m
    No registration         :crit, c2b, after c1, 60m
    Notification available  :done, c2a, after c1, 60m
    Normal                  :done, c3, after c2a c2b, 5m
```

Rollback:
1. *Promote* the temporary database to be the primary registration database
2. Switch all services to the temporary database

Advantages:
1. No downtime to breaking news notification
2. Registrations API downtime is shorter than in the option 2

Disadvantages:
1. Downtime to registrations API
2. Readers may find our system unreliable (subscribing to a topic but later find that it is not on our database)


### Option 4: Logical replication

We create an empty Postgresql 13 database and use logical replication to continuously replicate data from production database.

Logical replication has [some restrictions](https://www.postgresql.org/docs/10/logical-replication-restrictions.html), but they do not affect the registrations database as the restricted features are not used.

```mermaid
gantt
    title Approach 4 - Logical replicaton
    dateFormat  HH:mm:ss
    axisFormat  %H:%M:%S
    section PROD DB
    Enable replication                :a1, 00:00:00, 5m
    Create publication                :a2, after a1, 5m
    Synchronise                       :a3, after b1, 30m
    section PG13 DB
    Create subscription               :b1, after a2, 5m
    Synchronise                       :b2, after b1, 30m
    Stop subscription                 :b3, after b2, 5m
    Switchover                        :b4, after b3, 5m
    Serving all                       :milestone, after b4, 0m
    section Impact 
    No registration                   :crit, c1a, 00:00:00, 5m
    No notification                   :crit, c1b, 00:00:00, 5m
    Normal                            :done, c2, after c1a c1b, 45m
    Registration lost                 :crit, c3a, after b3, 5m
    Notification available            :done, c3b, after b3, 5m
    Normal                            :done, c4, after c3a c3b, 5m
```

Rollback:
1. Before switchover, no action
2. After switchover, switch back to the original PROD DB

Advantages:
1. Minimal downtime
2. Prepare an empty Postgresql 13.6 database beforehand
3. The initial 5-min downtime on notification can be avoided by setting up a temporary database to serve breaking news tool
4. Use Postgresql 13.6 directly (rather than 13.4)

Disadvantages:
1. More sophisticated operations

Procedure: https://aws.amazon.com/blogs/database/using-logical-replication-to-replicate-managed-amazon-rds-for-postgresql-and-amazon-aurora-to-self-managed-postgresql/

RDS Supports logical replication: https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_PostgreSQL.html#PostgreSQL.Concepts.General.FeatureSupport.LogicalReplication

Postgresql: https://www.postgresql.org/docs/10/logical-replication.html

### Option 5: Manual data copy

We create an empty Postgresql 13 database and use pg_dump to copy data across the database

```mermaid
gantt
    title Approach 5 - Manual data copy
    dateFormat  HH:mm:ss
    axisFormat  %H:%M:%S
    section PROD DB
    Create database dump              :a1, 00:00:00, 25m
    section PG13 DB
    Restore from dump                 :b1, after a1, 25m
    Reindexing and optimizer stats    :b2, after b1, 10m
    Switchover                        :b3, after b2, 5m
    Serving all                       :milestone, after b3, 0m
    section Impact 
    Registration lost                 :crit, c1a, 00:00:00, 65m
    Notification available            :done, c1b, 00:00:00, 65m
    Normal                            :done, c2, after b3, 5m
```

Rollback:
1. Before switchover, no action
2. After switchover, switch back to the original PROD DB

Advantages:
1. Prepare an empty Postgresql 13.6 database beforehand
2. Use Postgresql 13.6 directly (rather than 13.4)

Disadvantages:
1. Changes to registrations database that happen during upgrade will be lost after switchover
2. Readers may find our system unreliable (subscribing to a topic but later find that it is not on our database)

Reference: https://www.postgresql.org/docs/10/backup-dump.html

## Conclusion and Recommendation
Since the option 4 has the shortest downtime and the logical replication have been tested on the test rig database, it is recommended to upgrade the database from Postgresql 10.18 to Postgresql 13.6 using this approach.

The detailed procedures have been prepared in this [document](./03-postresql-upgrade-procedure.md).


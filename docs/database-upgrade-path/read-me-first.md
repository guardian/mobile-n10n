# Proposal: Service improvements on RDS and SQL side

The idea is to:

- Perform RDS upgrade from Postgres 13 -> 18 via Blue/Green approach
- Switch from `gp2` Storage class to `gp3`
- Rework `registrations` table structure
- Rework current `idx_registration_shard_topic` to get rid of bitmap-heap-scan bottleneck.

## Current RDS situation

Currently both `CODE` and `PROD` RDS instances of `mobile-n10n` service are running on Postgres 13. As of writing, the version 18.4 is the latest supported RDS for Postgres. Both instances are on `gp2` Storage type.

## Reasons to upgrade Postgres

Version 13 is deprecated on AWS since Feb 28, 2026. Also, AWS provides `gp3` Storage type.

### Postgres 13 to 18.4
Postgres versions 14–18 bring things that are relevant to our specific access pattern:

| Version | Relevant improvement | Why it matters to mobile-n10n |
|---|---|---|
| **14** | Reduced index bloat on frequently-updated indexed columns; faster VACUUM | The `registrations` table churns constantly (per-token insert/delete). The tuning docs show bloat directly degrades the hot query, forcing manual `REINDEX`/`VACUUM FULL`. This attacks the root cause. |
| **14** | `pg_stat_statements` tracks query planning time; better parallelism | We already rely on `pg_stat_statements` ([`03-postgresql-query-analyser.md`](https://github.com/guardian/mobile-n10n/blob/fd79e8049dd475abe58894d4e8066e5c053714b2/docs/testing/03-postgresql-query-analyser.md)). |
| **15** | Sort performance improvements | The query ends in `GROUP BY token, platform, buildTier` (a HashAggregate/sort step). |
| **16** | Better parallel hash-join / aggregate; query-plan-level gains | Marginal for a single-table scan, but helps the aggregate. |
| **17** | **Major VACUUM memory/perf overhaul** + B-tree improvements for `IN (...)` / multi-value lookups | Directly relevant: the query is `topic IN (...)` + a range scan on a B-tree index, and we fight VACUUM/bloat continuously. This is the most valuable single release for us. |
| **18.4** | Async I/O (io_uring) for sequential/bitmap heap reads; further planner and B-tree gains | **Potentially the most impactful for the bitmap heap scan** — but the payoff depends on storage being able to serve those reads, which loops back to gp3. |

### 2. Switch from `gp2` to `gp3` storage class
Our DB is currently:

```yaml name=registration-db.yaml url=https://github.com/guardian/mobile-n10n/blob/fd79e8049dd475abe58894d4e8066e5c053714b2/registration-db.yaml#L106-L107
      StorageEncrypted: True
      StorageType: gp2
```

With **gp2**, IOPS and throughput are *tied to volume size* (3 IOPS/GiB) and we rely on a **burst balance** that depletes under sustained load. That is exactly our failure mode — we even alarm on it:

```yaml name=registration-db.yaml url=https://github.com/guardian/mobile-n10n/blob/fd79e8049dd475abe58894d4e8066e5c053714b2/registration-db.yaml#L173-L178
  BurstBalanceAlarmAlert:
    ...
      AlarmDescription: The burst balance for the notifications registrations rds database has gone below the threshold, please think about scaling the database to allow for recovery time.
```

And the harvester's whole reason for the RDS proxy was **the DB not keeping up with concurrent I/O**, timing out and marking connections "broken" (replaying SQS messages) — see [`docs/testing/02-rds-proxy.md`](https://github.com/guardian/mobile-n10n/blob/fd79e8049dd475abe58894d4e8066e5c053714b2/docs/testing/02-rds-proxy.md). During a big breaking-news send, the harvester hammers the volume with random page reads — precisely when gp2 burst balance runs out.

**What gp3 gives for this workload:**

1. **Decoupled, provisioned IOPS & throughput.** gp3 baseline is **3,000 IOPS and 125 MB/s regardless of volume size**, and we can provision up to **16,000 IOPS / 1,000 MB/s** independently. Our bitmap-heap-scan bottleneck is *page reads* — more guaranteed IOPS directly shortens Step 2 of the query plan, the step our docs say dominates.
2. **No burst-balance cliff.** gp3 delivers a *consistent baseline* instead of a depleting bucket. This should reduce the harvester timeout/"marked as broken" errors that trigger SQS replays and the `BurstBalance` alarm — likely retiring that alarm entirely.
3. **Lower, more predictable performance during peaks.** Breaking-news spikes are when latency matters most (the 2-minute SLO); gp3 sustains throughput through the spike rather than degrading as credits drain.
4. **~20% cheaper storage** than gp2 for the same capacity, with baseline performance included — often a cost *reduction* even before we provision extra IOPS.
5. **Online conversion.** gp2→gp3 is a modify-volume operation with no downtime (brief "optimizing" period), unlike the engine upgrade.

## Why Blue/Green upgrade path

The Blue/Green switch/upgrade path will provide us least amount of RDS downtime during the switchover whilst simultaneously provide a revert path in case Blue/Green upgrade would fail at some stage by simply allowing us to keep on using the very same RDS instance.

Below are two documents that provide instructions what we should do to complete this switchover successfully. **Read those carefully:**

- We should exercise upgrading CODE RDS instance first - [n10n-code-rds-migration-playbook.md](./n10n-code-rds-migration-playbook.md)
- Once we are comfortable with CODE RDS upgrade, we are ready to switch over PROD - [n10n-prod-rds-migration-playbook.md](./n10n-prod-rds-migration-playbook.md)

## Schema/Index optimisations

There are a few other optimisations available at our disposal:

### Index

```sql
CREATE INDEX CONCURRENTLY idx_registration_shard_topic_covering
    ON registrations.registrations USING btree (shard, topic)
    INCLUDE (token, platform, buildtier);
```

By including `token`, `platform` and `buildtier` as payload (not key) columns, Postgres reads everything it needs from the index leaf pages and can skip the heap entirely — turning the Bitmap Heap Scan into an Index-Only Scan.

The ~30,736 heap-scan cost should collapse toward the ~1,688 index-scan cost.

### Registrations table structure

The `topic` and `patform` columns on the `registrations` are text type, to reduce index and `registrations` table sizes we could switch to normalised table layout by introducing `topics` table having unique topic names, and introducing `platforms` table OR switching `platform` column type to an `ENUM`.

These will reduce physical RDS data volume size and will speed up index lookups as the `topic` will be a matter of comparing `SMALLINT` OR `IDENTITY` column (both integers) vs string equality comparisons.
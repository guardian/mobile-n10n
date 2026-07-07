# PostgreSQL 13 → 18 Blue/Green Upgrade — PROD Runbook

**Scope:** Execute the registrations DB major-version upgrade (PostgreSQL 13 → 18) in **PROD** using an AWS
RDS Blue/Green Deployment, then reconcile CloudFormation state — without triggering instance replacement.

**Prerequisite:** The CODE rehearsal (see `n10n-code-rds-migration-playbook.md`) **must be completed
successfully** before this runbook is started. Timing figures from the rehearsal's timing log should be used
to size the PROD maintenance window.

**Why the proxy needs manual work:** The proxy CDK stack (`RegistrationsDbProxy-PROD`) binds to the Blue
instance's physical resource ID at deploy time via `ProxyTarget.fromInstance(...)`. AWS Blue/Green does
**not** repoint the proxy target group during switchover. Without the manual target-swap in Phase 4, all
worker lambdas (harvester, topic-counter, cleaner, registration-cleaning-worker) continue routing through
the proxy to the stale old-Blue instance.

> ⚠️ **PROD.** Every identifier here targets the PROD stack. All changes affect live user traffic.
> Run only inside the agreed maintenance window with the team on a call.

---

## Conventions

| Item | Value |
|---|---|
| Region | `eu-west-1` |
| AWS CLI profile | `mobile` |
| Stage | `PROD` |
| DB name | `registrationsPROD` |
| Schema | `registrations` |
| Master user | `root` (per procedure docs) |

Set these once in your shell so every command below is copy-paste-able:

```bash
export AWS_PROFILE=mobile
export AWS_REGION=eu-west-1
export STAGE=PROD

# From cdk/bin/cdk.ts (dbProxyProdProps) — VERIFY these are still current before the window:
export DB_INSTANCE_ID="notifications-registrations-db-private-prod"
export DB_HOST="notifications-registrations-db-private-prod.crwidilr2ofx.eu-west-1.rds.amazonaws.com"
export DB_NAME="registrationsPROD"
export DB_PROXY_NAME="registrations-db-proxy-cdk-prod"
```

> **Explanation:** Centralizing these avoids typos and makes it obvious if the PROD endpoint/identifier
> ever changes. `AWS_PROFILE`/`AWS_REGION` are read automatically by the AWS CLI.

---

## Before the maintenance window — Pre-flight checks (complete at least 24 h before)

These must be done in advance, not during the window. Failures here require rescheduling.

### P0.1 Confirm `<PG18_VERSION>` from the CODE rehearsal is still available

```bash
aws rds describe-db-engine-versions \
  --engine postgres \
  --query "DBEngineVersions[?starts_with(EngineVersion,'18.')].EngineVersion" \
  --output text
```
**What / why:** Confirms the exact minor version used in the CODE rehearsal is still offered in
`eu-west-1`. Use the identical version string as CODE — do not assume it is the same.

### P0.2 Confirm target instance class is orderable for PG18

```bash
aws rds describe-orderable-db-instance-options \
  --engine postgres \
  --engine-version <PG18_VERSION> \
  --query "OrderableDBInstanceOptions[].DBInstanceClass" \
  --output text | tr '\t' '\n' | sort -u | grep <PROD_CLASS>
```
**What / why:** PROD runs `m6g.large` (confirmed during the PG10→13 migration). Verify it is still
orderable for the chosen PG18 version. **Do not downsize.**

### P0.3 Re-confirm RDS Proxy supports PG18 (HARD BLOCKER if not)

```bash
aws rds describe-db-proxies \
  --db-proxy-name "$DB_PROXY_NAME" \
  --query "DBProxies[].{Name:DBProxyName,Engine:EngineFamily,Status:Status}" \
  --output table
```
**What / why:** `EngineFamily` must be `POSTGRESQL` and AWS docs must confirm PG18 is a supported
proxy target. If not confirmed, **STOP** — the migration cannot proceed while the proxy fronts the
worker lambdas.

### P0.4 Record current PROD instance reality

```bash
aws rds describe-db-instances \
  --db-instance-identifier "$DB_INSTANCE_ID" \
  --query "DBInstances[0].{Class:DBInstanceClass,Engine:EngineVersion,Storage:StorageType,AllocatedStorage:AllocatedStorage,Iops:Iops,MultiAZ:MultiAZ,PG:DBParameterGroups[0].DBParameterGroupName}" \
  --output table
```
**What / why:** Captures the exact configuration Green must match or exceed. Multi-AZ must remain
enabled on Green. Record these values and carry them into Phase 3.

### P0.5 Check for leftover logical-replication artifacts

Connect to PROD via the SSM tunnel (Appendix A), then:

```sql
SELECT * FROM pg_publication;
SELECT subname, subenabled FROM pg_subscription;
SELECT slot_name, plugin, active FROM pg_replication_slots;
```
**What / why:** Stray publications/subscriptions/slots will cause Blue/Green creation to fail its
pre-checks. Drop any that are confirmed unused:
```sql
DROP PUBLICATION IF EXISTS alltables;
DROP SUBSCRIPTION IF EXISTS mysub;
SELECT pg_drop_replication_slot('<slot_name>');
```

### P0.6 Verify schema is replication-safe

```sql
SELECT t.tablename
FROM pg_tables t
WHERE t.schemaname = 'registrations'
  AND NOT EXISTS (
    SELECT 1
    FROM information_schema.table_constraints c
    WHERE c.table_schema = t.schemaname
      AND c.table_name  = t.tablename
      AND c.constraint_type = 'PRIMARY KEY'
  );
```
**What / why:** Blue/Green logical replication requires a PK. The `registrations` table has
`PRIMARY KEY (token, topic)` — this should return zero rows. If anything appears, apply
`ALTER TABLE registrations.<table> REPLICA IDENTITY FULL;` before proceeding.

### P0.7 List installed extensions

```sql
SELECT extname, extversion FROM pg_extension ORDER BY extname;
```
**What / why:** Extension versions pinned to PG13 can block the upgrade. Update any outdated
extensions with `ALTER EXTENSION <name> UPDATE;`.

### P0.8 Capture a production performance baseline

```sql
SELECT COUNT(1) FROM registrations.registrations;
SELECT platform, COUNT(1) FROM registrations.registrations GROUP BY platform;

EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(1) FROM registrations.registrations WHERE topic = 'breaking/uk';

SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'registrations' AND tablename = 'registrations';
```
**What / why:** Baseline row counts and query plans (especially `idx_registration_shard_topic`) to
compare against post-upgrade. Store the output — you'll need it to sign off Phase 4.

### P0.9 Stage the PG18 parameter group (via PR + Riff-Raff)

> All CloudFormation changes go through Riff-Raff from `registration-db.yaml`, triggered by
> `.github/workflows/registration.yml`. Never run a console/CLI `update-stack` directly.

**If the parameter group was already deployed to PROD as part of the CODE rehearsal PR, skip this step.**
Otherwise, open a PR adding this to `registration-db.yaml` without touching the existing instance resource:

```yaml
  RegistrationsPg18ParameterGroup:
    Type: AWS::RDS::DBParameterGroup
    Properties:
      Description: PostgreSQL 18 parameter group for registrations DB
      Family: postgres18
      Parameters: {}
```

Merge and let Riff-Raff deploy to **PROD**. Then capture the physical name:

```bash
aws rds describe-db-parameter-groups \
  --query "DBParameterGroups[?starts_with(DBParameterGroupName, 'registrations') && DBParameterGroupFamily=='postgres18'].DBParameterGroupName" \
  --output text
```

Save the output as `<PG18_PARAM_GROUP>`.

**✅ Pre-flight exit criteria:** PG18 version confirmed · PROD class orderable · proxy PG18 support
confirmed · no stray replication objects · schema PK-safe · baseline captured · PG18 parameter group
deployed.

---

## Maintenance window — Phase 1: Create and monitor the Blue/Green deployment

> The Blue/Green creation itself causes **no downtime** — it runs in the background while PROD
> continues serving traffic normally. Start this at the beginning of the window so replication
> has time to catch up before you need to cut over.

### 1.1 Get the source instance ARN

```bash
export SOURCE_ARN=$(aws rds describe-db-instances \
  --db-instance-identifier "$DB_INSTANCE_ID" \
  --query "DBInstances[0].DBInstanceArn" --output text)
echo "$SOURCE_ARN"
```

### 1.2 Create the Blue/Green deployment

```bash
aws rds create-blue-green-deployment \
  --blue-green-deployment-name "pg13-to-pg18-prod" \
  --source "$SOURCE_ARN" \
  --target-engine-version "<PG18_VERSION>" \
  --target-db-instance-class "<CLASS_FROM_P0.4>" \
  --target-storage-type "<gp2|gp3 matching P0.4>" \
  --target-db-parameter-group-name "<PG18_PARAM_GROUP>"
```
**What / why:** Creates a Green clone upgraded to PG18, kept in sync with Blue via logical
replication. Green runs independently — PROD traffic is unaffected while this runs.

Capture the identifier:
```bash
export BG_ID=$(aws rds describe-blue-green-deployments \
  --query "BlueGreenDeployments[?BlueGreenDeploymentName=='pg13-to-pg18-prod'].BlueGreenDeploymentIdentifier | [0]" \
  --output text)
echo "$BG_ID"
```

### 1.3 Monitor until AVAILABLE and replication lag ≈ 0

```bash
# Poll status
aws rds describe-blue-green-deployments \
  --blue-green-deployment-identifier "$BG_ID" \
  --query "BlueGreenDeployments[0].{Status:Status,Tasks:Tasks}" \
  --output json
```

Identify the Green instance identifier:
```bash
aws rds describe-blue-green-deployments \
  --blue-green-deployment-identifier "$BG_ID" \
  --query "BlueGreenDeployments[0].SwitchoverDetails" --output json
```

Watch replication lag (must be at/near 0 before switchover):
```bash
aws cloudwatch get-metric-statistics \
  --namespace AWS/RDS --metric-name ReplicaLag \
  --dimensions Name=DBInstanceIdentifier,Value=<GREEN_INSTANCE_ID> \
  --start-time "$(date -u -d '-15 min' +%FT%TZ)" \
  --end-time "$(date -u +%FT%TZ)" \
  --period 60 --statistics Average --output table
```
**What / why:** Do not proceed to switchover until `Status=AVAILABLE` and lag is 0 or within a
single polling period of 0. Any unresolved lag becomes lost writes.

### 1.4 Pre-cutover validation on Green

Connect to Green directly (Appendix A, substitute `<GREEN_INSTANCE_ENDPOINT>`):
```sql
SELECT version();                                   -- must report PostgreSQL 18.x
SELECT COUNT(1) FROM registrations.registrations;   -- must match Blue row count
ANALYZE VERBOSE;                                    -- pre-warm optimizer stats before cutover
```
**What / why:** Row-count parity confirms replication is complete. `ANALYZE VERBOSE` reduces the
optimizer stats gap immediately after switchover so harvester query plans stay optimal.

**✅ Phase 1 exit criteria:** Status `AVAILABLE` · lag ≈ 0 · Green reports PG18 · row count matches
· `ANALYZE` done on Green.

---

## Maintenance window — Phase 2: Switchover + immediate proxy target-swap

> This is the highest-risk phase. The write block and proxy gap both happen here.
> Have the rollback commands (Phase 3) open and ready before executing 2.2.

### 2.1 Stage the proxy details BEFORE switchover (do not skip)

```bash
aws rds describe-db-proxy-targets --db-proxy-name "$DB_PROXY_NAME" \
  --query "Targets[].{Id:RdsResourceId,Type:Type,Endpoint:Endpoint,State:TargetHealth.State}" \
  --output table

aws rds describe-db-proxy-target-groups --db-proxy-name "$DB_PROXY_NAME" \
  --query "TargetGroups[].TargetGroupName" --output text
```
**What / why:** Records the stale Blue `RdsResourceId` you will deregister in 2.3. Without this,
you will be looking it up under time pressure while worker lambdas are routed to the wrong instance.

Note down: **`<STALE_INSTANCE_ID>`** = the `RdsResourceId` currently in the proxy target group
(this is the Blue instance, which will be renamed to `-old1` or similar after switchover).

### 2.2 Execute the switchover

```bash
aws rds switchover-blue-green-deployment \
  --blue-green-deployment-identifier "$BG_ID"
```
**What / why:** AWS briefly blocks writes (~30–60 s based on CODE rehearsal), drains remaining
replication lag, promotes Green to the original identifier/endpoint, and renames Blue with an `-old`
suffix. The registration ASG (which connects directly via the preserved endpoint) resumes
automatically. Worker lambda traffic is broken until 2.3 completes.

Watch for completion:
```bash
aws rds describe-blue-green-deployments \
  --blue-green-deployment-identifier "$BG_ID" \
  --query "BlueGreenDeployments[0].Status" --output text
```
Wait for `SWITCHOVER_COMPLETED` before continuing.

### 2.3 Re-point the RDS Proxy (IMMEDIATELY after SWITCHOVER_COMPLETED)

```bash
# Deregister the stale Blue (now renamed to *-old)
aws rds deregister-db-proxy-targets \
  --db-proxy-name "$DB_PROXY_NAME" \
  --db-instance-identifiers "<STALE_INSTANCE_ID>"

# Register the promoted Green (which now holds the original identifier)
aws rds register-db-proxy-targets \
  --db-proxy-name "$DB_PROXY_NAME" \
  --db-instance-identifiers "$DB_INSTANCE_ID"
```
**What / why:** This is the manual step Blue/Green does not perform. Every second between
`SWITCHOVER_COMPLETED` and this command completing is a second where harvester, topic-counter,
cleaner, and registration-cleaning-worker lambdas are routing through the proxy to the stale Blue.

Confirm the new target is healthy:
```bash
aws rds describe-db-proxy-targets --db-proxy-name "$DB_PROXY_NAME" \
  --query "Targets[].{Id:RdsResourceId,State:TargetHealth.State,Reason:TargetHealth.Reason}" \
  --output table
```
`State` must be `AVAILABLE` before you proceed.

### 2.4 Verify the registration ASG path

```bash
aws ssm get-parameter \
  --name "/notifications/PROD/mobile-notifications/registration.db.url" \
  --query "Parameter.Value" --output text
```
**What / why:** The ASG connects directly via an instance DNS endpoint, which Blue/Green preserves.
This should still resolve correctly — but confirm the SSM value matches `$DB_HOST`. No update
should be needed.

### 2.5 Post-cutover SQL maintenance

Connect to the promoted PROD instance (Appendix A):
```sql
-- 1) Rebuild optimizer statistics (reset by major version upgrade)
ANALYZE VERBOSE;

-- 2) Reindex the secondary index for collation safety — CONCURRENTLY to avoid table locks
REINDEX INDEX CONCURRENTLY registrations.idx_registration_shard_topic;
```
**What / why:**
- `ANALYZE VERBOSE` is essential before the next breaking news notification is sent — without it
  the planner may choose a sequential scan over the index.
- `REINDEX … CONCURRENTLY` avoids the `ACCESS EXCLUSIVE` lock that `REINDEX DATABASE` would take,
  which would outage the registration API. It runs alongside live traffic at the cost of taking
  longer.

### 2.6 Functional end-to-end verification

```bash
aws lambda invoke \
  --function-name "mobile-notifications-fakebreakingnews-PROD" \
  --payload '{}' /tmp/fakebreakingnews-prod.json
cat /tmp/fakebreakingnews-prod.json
```
**What / why:** Fires the full notification path (workers → proxy → upgraded PROD DB) using the
same dry-run lambda used throughout the procedure docs. A successful response here means the
upgrade is live and traffic is flowing correctly.

Compare row counts against the P0.8 baseline:
```sql
SELECT COUNT(1) FROM registrations.registrations;
SELECT platform, COUNT(1) FROM registrations.registrations GROUP BY platform;

EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(1) FROM registrations.registrations WHERE topic = 'breaking/uk';
```

**✅ Phase 2 exit criteria:** `SWITCHOVER_COMPLETED` · proxy target `AVAILABLE` on promoted instance
· ASG SSM URL verified · `ANALYZE` + concurrent reindex done · fake breaking news returns success ·
row counts match baseline.

---

## Phase 3 — Rollback procedure (if Phase 2 fails)

> Rollback is only possible while the old Blue instance (`<OLD_BLUE_INSTANCE_ID>`) still exists.
> Do not delete it until you have observed PROD for a safe period (see Phase 5).

**If switchover has not yet been triggered:** delete the Blue/Green deployment; Blue is untouched.
```bash
aws rds delete-blue-green-deployment \
  --blue-green-deployment-identifier "$BG_ID" \
  --delete-target false
```

**If switchover has completed but the proxy is not healthy or fake breaking news fails:**
repoint the proxy back to the retained old Blue:
```bash
# Re-register the old Blue
aws rds register-db-proxy-targets \
  --db-proxy-name "$DB_PROXY_NAME" \
  --db-instance-identifiers "<OLD_BLUE_INSTANCE_ID>"

# Deregister the promoted Green
aws rds deregister-db-proxy-targets \
  --db-proxy-name "$DB_PROXY_NAME" \
  --db-instance-identifiers "$DB_INSTANCE_ID"
```

Verify the proxy is healthy on the old Blue:
```bash
aws rds describe-db-proxy-targets --db-proxy-name "$DB_PROXY_NAME" \
  --query "Targets[].{Id:RdsResourceId,State:TargetHealth.State,Reason:TargetHealth.Reason}" \
  --output table
```

Then re-run the fake breaking news check to confirm service is restored:
```bash
aws lambda invoke \
  --function-name "mobile-notifications-fakebreakingnews-PROD" \
  --payload '{}' /tmp/fakebreakingnews-prod-rollback.json
cat /tmp/fakebreakingnews-prod-rollback.json
```

> After rollback, the ASG path continues working because it uses the preserved endpoint. No SSM
> change is needed.

**Post-rollback actions:** open an incident, review logs, do not reattempt the upgrade without
understanding the root cause.

---

## Phase 4 — Reconcile CloudFormation WITHOUT replacement (via Riff-Raff)

> Goal: bring `registration-db.yaml` into agreement with the promoted PG18 instance while
> CloudFormation reports `Modify / Replacement=False`. Do this after PROD has been observed
> stable — it does not need to happen inside the maintenance window.

### 4.1 Confirm CFN physical ID still matches the promoted instance

```bash
aws cloudformation describe-stack-resources \
  --stack-name <PROD_DB_STACK_NAME> \
  --logical-resource-id PrivateRegistrationPostgres13DB \
  --query "StackResources[0].{Physical:PhysicalResourceId,Status:ResourceStatus}" \
  --output table
```
**What / why:** After Blue/Green, the promoted instance retains the original identifier, so CFN's
`PhysicalResourceId` should still match `$DB_INSTANCE_ID`. If it does **not** match, use the
import path (4.4) instead of a normal update — otherwise CFN will delete and recreate the DB.

### 4.2 Open a PR editing ONLY property values

In `registration-db.yaml`, on `PrivateRegistrationPostgres13DB`, change **values only**:
- `EngineVersion: <PG18_VERSION>`
- `StorageType:` / `DBInstanceClass:` only if you actually changed them in Phase 1.2
- Add `DBParameterGroupName: !Ref RegistrationsPg18ParameterGroup`

**Do NOT** rename the logical ID `PrivateRegistrationPostgres13DB` or the `DBInstanceIdentifier`
string. Renaming either causes CloudFormation to treat the resource as new → replacement → data loss.

### 4.3 Verify the ChangeSet shows no replacement

```bash
aws cloudformation create-change-set \
  --stack-name <PROD_DB_STACK_NAME> \
  --change-set-name pg18-reconcile-prod \
  --use-previous-template=false \
  --template-body file://registration-db.yaml \
  --parameters ParameterKey=Stage,UsePreviousValue=true \
               ParameterKey=Postgres13InstanceType,UsePreviousValue=true \
               ParameterKey=AllocatedStorage,UsePreviousValue=true \
               ParameterKey=VpcId,UsePreviousValue=true \
               ParameterKey=PrivateVpcSubnets,UsePreviousValue=true \
               ParameterKey=MasterUserName,UsePreviousValue=true \
               ParameterKey=MasterPasswordReference,UsePreviousValue=true \
               ParameterKey=AlarmTopic,UsePreviousValue=true

aws cloudformation describe-change-set \
  --stack-name <PROD_DB_STACK_NAME> \
  --change-set-name pg18-reconcile-prod \
  --query "Changes[].ResourceChange.{LogicalId:LogicalResourceId,Action:Action,Replacement:Replacement}" \
  --output table
```
**What / why:** The RDS instance row **must** show `Action=Modify` and `Replacement=False`.
If it shows `Replace` or `Add`, **abort** — do not execute the ChangeSet. Investigate state
alignment via 4.4.

### 4.4 (Only if 4.1 mismatched) Import the real instance into CFN state

```bash
aws cloudformation create-change-set \
  --stack-name <PROD_DB_STACK_NAME> \
  --change-set-name pg18-import-prod \
  --change-set-type IMPORT \
  --resources-to-import '[{
      "ResourceType":"AWS::RDS::DBInstance",
      "LogicalResourceId":"PrivateRegistrationPostgres13DB",
      "ResourceIdentifier":{"DBInstanceIdentifier":"'"$DB_INSTANCE_ID"'"}
  }]' \
  --template-body file://registration-db.yaml
```
**What / why:** Re-binds CloudFormation to the live instance without recreating it.

### 4.5 Deploy via Riff-Raff

Merge the PR and let Riff-Raff deploy to PROD. Do **not** click "Update Stack" in the console.

### 4.6 Confirm no drift

```bash
DRIFT_ID=$(aws cloudformation detect-stack-drift --stack-name <PROD_DB_STACK_NAME> \
  --query StackDriftDetectionId --output text)
aws cloudformation describe-stack-resource-drifts \
  --stack-name <PROD_DB_STACK_NAME> \
  --query "StackResourceDrifts[?StackResourceDriftStatus!='IN_SYNC'].{Id:LogicalResourceId,Status:StackResourceDriftStatus}" \
  --output table
```

**✅ Phase 4 exit criteria:** ChangeSet showed `Modify/False` (or import succeeded) · Riff-Raff
deploy green · drift status `IN_SYNC`.

---

## Phase 5 — Post-upgrade cleanup (after safe observation period)

> Wait at least **48 hours** of normal PROD traffic, including at least one breaking news
> notification, before proceeding. The old Blue instance is your rollback target until you
> delete it.

### 5.1 Take a final snapshot of the old Blue

```bash
aws rds create-db-snapshot \
  --db-instance-identifier "<OLD_BLUE_INSTANCE_ID>" \
  --db-snapshot-identifier "registrations-prod-preupgrade-pg13-final"
```
**What / why:** Provides a point-in-time restore option for the PG13 database even after the
instance is deleted.

### 5.2 Delete the old Blue instance

```bash
aws rds delete-db-instance \
  --db-instance-identifier "<OLD_BLUE_INSTANCE_ID>" \
  --final-db-snapshot-identifier "registrations-prod-pg13-deletion-final"
```
**What / why:** Removes the cost of running a second full PROD-sized RDS instance. The two
snapshots (5.1 + the `--final-db-snapshot-identifier` here) give you two recovery points.

### 5.3 Clean up psql history on jump hosts

```bash
rm ~/.psql_history
```
**What / why:** psql history can contain plaintext passwords entered during the upgrade. The
existing procedure docs call this out explicitly.

**✅ Phase 5 exit criteria:** 48 h observation period complete · at least one breaking news
notification sent successfully · old Blue snapshot taken · old Blue instance deleted.

---

## Maintenance window timing log

Fill in actual times during the window. Times from the CODE rehearsal are shown for comparison.

| Step | CODE rehearsal | PROD start | PROD end | PROD duration | Notes |
|---|---|---|---|---|---|
| B/G create (1.2) | | | | | |
| Reach `AVAILABLE` + lag 0 (1.3) | | | | | |
| Switchover (2.2) | | | | write-block duration |
| Proxy repoint healthy (2.3) | | | | **key metric** |
| Fake breaking news pass (2.6) | | | | |
| `ANALYZE` + reindex (2.5) | | | | |
| CFN reconcile (4.x) | | | | can be post-window |

---

## Appendix A — psql connection helper (via SSM tunnel)

```bash
# Terminal 1 — open an SSH tunnel via SSM
eval $(ssm ssh --tags registration,mobile-notifications,PROD -p mobile --raw --newest) \
  -L 5432:${DB_HOST}:5432

# Terminal 2 — fetch the master password from SSM
aws --profile=mobile --region=eu-west-1 ssm get-parameter --with-decryption \
  --name /notifications/PROD/registrations-db-password | jq -r .Parameter.Value

# Terminal 3 — connect (paste the password when prompted)
psql -h localhost -U root ${DB_NAME}
```
To connect to the Green instance before switchover (Phase 1.4), substitute
`<GREEN_INSTANCE_ENDPOINT>` for `${DB_HOST}` in the `-L` port forward.

---

## Quick reference — the five things that most often go wrong

1. **Proxy target-swap is manual** (2.3). Blue/Green won't do it; it's what keeps the worker
   lambdas (harvester, topic-counter, cleaner, registration-cleaning-worker) alive.
2. **PG18 must be supported by the RDS Proxy** (P0.3) or the whole upgrade is blocked at the
   architecture level.
3. **Never rename the CFN logical ID / `DBInstanceIdentifier`** (4.2) — CloudFormation treats it
   as a new resource, deletes the old one, and creates a blank DB.
4. **`REINDEX … CONCURRENTLY`, never `REINDEX DATABASE`** (2.5) — the latter takes
   `ACCESS EXCLUSIVE` locks on every table and causes a registration API outage.
5. **All CFN changes via PR + Riff-Raff** (Phases P0.9 and 4) — never via console or CLI
   `update-stack`, which will be reverted by the next CI deploy and leave the stack in drift.

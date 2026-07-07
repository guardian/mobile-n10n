# PostgreSQL 13 → 18 Blue/Green Upgrade — CODE Rehearsal Runbook

**Scope:** Rehearse the registrations DB major-version upgrade (PostgreSQL 13 → 18) in **CODE only**, using an AWS RDS Blue/Green Deployment, then reconcile CloudFormation state — without triggering instance replacement.

**Why a rehearsal:** This system fronts the registrations DB with an **RDS Proxy** used by the worker lambdas, while the registration ASG connects directly. AWS Blue/Green does **not** repoint the proxy for you. This runbook proves the full cutover (including the manual proxy target-swap and CloudFormation reconciliation) before it is attempted on PROD.

> ⚠️ **CODE ONLY.** Every identifier here targets the CODE stack. Do not run against PROD.

---

## Conventions

| Item | Value |
|---|---|
| Region | `eu-west-1` |
| AWS CLI profile | `mobile` |
| Stage | `CODE` |
| DB name | `registrationsCODE` |
| Schema | `registrations` |
| Master user | `root` (per procedure docs) |

Set these once in your shell so every command below is copy-paste-able:

```bash
export AWS_PROFILE=mobile
export AWS_REGION=eu-west-1
export STAGE=CODE

# From cdk/bin/cdk.ts (dbProxyCodeProps) — VERIFY these are still current:
export DB_INSTANCE_ID="notifications-registrations-db-private-code"
export DB_HOST="notifications-registrations-db-private-code.crwidilr2ofx.eu-west-1.rds.amazonaws.com"
export DB_NAME="registrationsCODE"
export DB_PROXY_NAME="registrations-db-proxy-cdk-code"
```

> **Explanation:** Centralizing these avoids typos and makes it obvious if the CODE endpoint/identifier ever changes. `AWS_PROFILE`/`AWS_REGION` are read automatically by the AWS CLI.

---

## Phase 0 — Feasibility discovery (run first; may be a hard blocker)

### 0.1 Find the real PG18 minor version available in your region

```bash
aws rds describe-db-engine-versions \
  --engine postgres \
  --query "DBEngineVersions[?starts_with(EngineVersion,'18.')].EngineVersion" \
  --output text
```
**What / why:** Lists the PostgreSQL 18.x minor versions RDS actually offers in `eu-west-1`. There is **no "18.4"** — pick a real value (e.g. `18.0`) and use it everywhere below as `<PG18_VERSION>`.

### 0.2 Confirm your target instance class is orderable for that version

```bash
aws rds describe-orderable-db-instance-options \
  --engine postgres \
  --engine-version <PG18_VERSION> \
  --query "OrderableDBInstanceOptions[].DBInstanceClass" \
  --output text | tr '\t' '\n' | sort -u
```
**What / why:** Ensures the class you intend for Green (e.g. your current PROD-equivalent class) is available for that PG18 minor version and region. **Do not blindly downsize to `db.t4g.medium`** — match what CODE actually runs today (see 0.4).

### 0.3 Confirm RDS Proxy supports PostgreSQL 18 (POTENTIAL HARD BLOCKER)

```bash
# There is no single CLI flag for "does proxy support PG18"; verify via docs + a describe on the existing proxy engine family:
aws rds describe-db-proxies \
  --db-proxy-name "$DB_PROXY_NAME" \
  --query "DBProxies[].{Name:DBProxyName,Engine:EngineFamily,Status:Status}" \
  --output table
```
**What / why:** Your architecture doc pins you to PG13 **because RDS Proxy historically capped supported engine versions**. `EngineFamily` should be `POSTGRESQL`. **Before proceeding, confirm in the AWS RDS Proxy docs that PG18 is a supported target in `eu-west-1`.** If it is not supported, **STOP** — the migration is blocked while the proxy remains in front of the workers.

### 0.4 Record current CODE instance reality (so Green matches)

```bash
aws rds describe-db-instances \
  --db-instance-identifier "$DB_INSTANCE_ID" \
  --query "DBInstances[0].{Class:DBInstanceClass,Engine:EngineVersion,Storage:StorageType,AllocatedStorage:AllocatedStorage,Iops:Iops,MultiAZ:MultiAZ,PG:DBParameterGroups[0].DBParameterGroupName}" \
  --output table
```
**What / why:** Captures the current class, engine version, storage type, size, IOPS and Multi-AZ. Your Green target should **match or exceed** these. The live template says `gp2`; confirm what's actually deployed.

### 0.5 Check for leftover logical-replication artifacts (from the old PG10→13 migration)

Open a psql session (see the connection helper in **Appendix A**), then:

```sql
-- Publications left behind by a previous migration
SELECT * FROM pg_publication;

-- Subscriptions on this instance
SELECT subname, subenabled FROM pg_subscription;

-- Replication slots still present/active
SELECT slot_name, plugin, active FROM pg_replication_slots;
```
**What / why:** Stray publications/subscriptions/slots can cause Blue/Green creation to fail its pre-checks. If found and confirmed unused, drop them:
```sql
-- Only if confirmed unused:
DROP PUBLICATION IF EXISTS alltables;
DROP SUBSCRIPTION IF EXISTS mysub;         -- disable first if it errors: ALTER SUBSCRIPTION mysub DISABLE;
SELECT pg_drop_replication_slot('<slot_name>');
```

### 0.6 Verify schema is replication-safe (primary keys)

```sql
-- Any tables in the registrations schema WITHOUT a primary key?
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
**What / why:** Blue/Green uses logical replication, which needs a PK (or `REPLICA IDENTITY FULL`) to stream UPDATE/DELETE. The `registrations` table already has `PRIMARY KEY (token, topic)`, so this should return **zero rows**. If anything shows up:
```sql
ALTER TABLE registrations.<table> REPLICA IDENTITY FULL;
```

### 0.7 List installed extensions

```sql
SELECT extname, extversion FROM pg_extension ORDER BY extname;
```
**What / why:** Old extension versions pinned to v13 can block a major upgrade. You likely have only `plpgsql`. Update anything outdated with `ALTER EXTENSION <name> UPDATE;`.

**✅ Phase 0 exit criteria:** real `<PG18_VERSION>` chosen · target class orderable · **proxy-supports-PG18 confirmed** · Green target matched to current CODE · no stray replication objects · schema PK-safe.

---

## Phase 1 — Capture a performance baseline (for regression comparison)

Run in psql against CODE **before** any change:

```sql
-- Row counts (sanity + lazy-load warm-up)
SELECT COUNT(1) FROM registrations.registrations;
SELECT platform, COUNT(1) FROM registrations.registrations GROUP BY platform;

-- Confirm the hot secondary index exists and capture a representative plan
EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(1) FROM registrations.registrations WHERE topic = 'breaking/uk';

-- Inspect indexes on the table
SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'registrations' AND tablename = 'registrations';
```
**What / why:** Establishes counts and query plans (especially anything using `idx_registration_shard_topic`) so you can prove post-upgrade parity. `EXPLAIN (ANALYZE, BUFFERS)` shows the real execution plan and IO.

---

## Phase 2 — Create the PG18 parameter group (via PR + Riff-Raff, not console/CLI)

> This stack deploys through **Riff-Raff** from `registration-db.yaml` (see `registration/conf/riff-raff.yaml` → `registrations-db-cfn`, triggered by `.github/workflows/registration.yml`). Do CFN changes as a PR, not a laptop `update-stack`.

**PR change:** add a new parameter-group resource to `registration-db.yaml`. **Do not modify the `PrivateRegistrationPostgres13DB` block, its logical ID, or its `DBInstanceIdentifier` in this PR.**

```yaml
  RegistrationsPg18ParameterGroup:
    Type: AWS::RDS::DBParameterGroup
    Properties:
      Description: PostgreSQL 18 parameter group for registrations DB
      Family: postgres18
      # Start conservative — decouple autovacuum tuning from the version cutover.
      Parameters: {}
```
**What / why:** You cannot attach a PG13 parameter group to a PG18 instance, so the group must exist first. `Family: postgres18` is correct for PG18. Keep parameters empty/default initially — one change at a time.

**Deploy:** merge and let Riff-Raff deploy to **CODE**. Then read back the physical name for use in the CLI:

```bash
aws rds describe-db-parameter-groups \
  --query "DBParameterGroups[?starts_with(DBParameterGroupName, 'registrations') && DBParameterGroupFamily=='postgres18'].DBParameterGroupName" \
  --output text
```
**What / why:** Captures the generated parameter-group name to pass to `create-blue-green-deployment` as `<PG18_PARAM_GROUP>`.

---

## Phase 3 — Create and monitor the Blue/Green deployment (CODE)

### 3.1 Get the source instance ARN

```bash
export SOURCE_ARN=$(aws rds describe-db-instances \
  --db-instance-identifier "$DB_INSTANCE_ID" \
  --query "DBInstances[0].DBInstanceArn" --output text)
echo "$SOURCE_ARN"
```
**What / why:** Blue/Green needs the ARN of the existing (Blue) instance as its source.

### 3.2 Create the Blue/Green deployment

```bash
aws rds create-blue-green-deployment \
  --blue-green-deployment-name "pg13-to-pg18-rehearsal-code" \
  --source "$SOURCE_ARN" \
  --target-engine-version "<PG18_VERSION>" \
  --target-db-instance-class "<TARGET_CLASS_FROM_0.4>" \
  --target-storage-type "<gp2|gp3 matching or upgrading current>" \
  --target-db-parameter-group-name "<PG18_PARAM_GROUP>"
```
**What / why:** Spins up a Green environment: a clone upgraded to PG18, on your chosen class/storage/param-group, kept in sync via logical replication. **Match `--target-db-instance-class` and `--target-storage-type` to Phase 0.4** — do not silently downsize or change storage unless intended.

Capture the identifier:
```bash
export BG_ID=$(aws rds describe-blue-green-deployments \
  --query "BlueGreenDeployments[?BlueGreenDeploymentName=='pg13-to-pg18-rehearsal-code'].BlueGreenDeploymentIdentifier | [0]" \
  --output text)
echo "$BG_ID"
```

### 3.3 Monitor until healthy and lag is ~0

```bash
aws rds describe-blue-green-deployments \
  --blue-green-deployment-identifier "$BG_ID" \
  --query "BlueGreenDeployments[0].{Status:Status,Tasks:Tasks}" \
  --output json
```
**What / why:** Poll until `Status` is `AVAILABLE` and all provisioning `Tasks` are complete. **Do not cut over early.**

Check replication lag on the Green instance (Green identifier ends with a Blue/Green suffix — read it from the details):
```bash
# Identify the Green (target) instance member
aws rds describe-blue-green-deployments \
  --blue-green-deployment-identifier "$BG_ID" \
  --query "BlueGreenDeployments[0].SwitchoverDetails" --output json
```
```bash
# Then watch replica lag on the green instance:
aws cloudwatch get-metric-statistics \
  --namespace AWS/RDS --metric-name ReplicaLag \
  --dimensions Name=DBInstanceIdentifier,Value=<GREEN_INSTANCE_ID> \
  --start-time "$(date -u -d '-15 min' +%FT%TZ)" \
  --end-time "$(date -u +%FT%TZ)" \
  --period 60 --statistics Average --output table
```
**What / why:** `ReplicaLag` must be at/near **0** before switchover so no writes are lost. `SwitchoverDetails` tells you the Blue and Green member identifiers.

### 3.4 Pre-cutover validation on Green (connect directly to the Green endpoint)

Using **Appendix A** but tunnelling to `<GREEN_INSTANCE_ENDPOINT>`:
```sql
SELECT version();                                   -- confirm it reports PostgreSQL 18.x
SELECT COUNT(1) FROM registrations.registrations;   -- parity vs. Blue
ANALYZE VERBOSE;                                     -- pre-build optimizer stats on Green
```
**What / why:** Confirms the engine really is PG18, data parity with Blue, and pre-populates `pg_statistic` so the post-cutover stats gap is minimal. (`ANALYZE VERBOSE` prints progress per table.)

**✅ Phase 3 exit criteria:** Status `AVAILABLE` · lag ≈ 0 · Green reports PG18 · counts match · `ANALYZE` done on Green.

---

## Phase 4 — Switchover (CODE) + the critical proxy target-swap

### 4.1 Stage the proxy details BEFORE switchover

```bash
aws rds describe-db-proxy-targets --db-proxy-name "$DB_PROXY_NAME" \
  --query "Targets[].{Id:RdsResourceId,Type:Type,Endpoint:Endpoint,State:TargetHealth.State}" \
  --output table

aws rds describe-db-proxy-target-groups --db-proxy-name "$DB_PROXY_NAME" \
  --query "TargetGroups[].TargetGroupName" --output text
```
**What / why:** You must know the proxy's current target and target-group name so you can repoint it **immediately** after switchover. **This is the step Blue/Green does NOT do for you**, and it's what keeps the worker lambdas (harvester, topic-counter, cleaner, registration-cleaning-worker) alive.

### 4.2 Execute the switchover

```bash
aws rds switchover-blue-green-deployment \
  --blue-green-deployment-identifier "$BG_ID"
```
**What / why:** AWS briefly blocks writes on Blue, drains remaining replication, promotes Green to the **original instance identifier/endpoint**, and renames the old Blue with an `-old` suffix. Typically 30–60s.

Watch it complete:
```bash
aws rds describe-blue-green-deployments \
  --blue-green-deployment-identifier "$BG_ID" \
  --query "BlueGreenDeployments[0].Status" --output text
```
**What / why:** Wait for `SWITCHOVER_COMPLETED`.

### 4.3 Re-point the RDS Proxy at the promoted instance (IMMEDIATELY)

Because the CDK proxy uses `ProxyTarget.fromInstance(...)`, the proxy may still reference the pre-swap instance. Deregister the stale target and register the promoted one:

```bash
# Remove the now-stale target (the old Blue, or a target no longer healthy)
aws rds deregister-db-proxy-targets \
  --db-proxy-name "$DB_PROXY_NAME" \
  --db-instance-identifiers "<STALE_INSTANCE_ID>"

# Register the promoted instance (it retains the ORIGINAL identifier "$DB_INSTANCE_ID")
aws rds register-db-proxy-targets \
  --db-proxy-name "$DB_PROXY_NAME" \
  --db-instance-identifiers "$DB_INSTANCE_ID"
```
**What / why:** Re-associates the proxy with the correct backend so lambda traffic flows to the upgraded DB. Because switchover gives Green the **original** identifier/endpoint, `$DB_INSTANCE_ID` is usually correct again — but verify with 4.1's output which member is stale.

Confirm health:
```bash
aws rds describe-db-proxy-targets --db-proxy-name "$DB_PROXY_NAME" \
  --query "Targets[].{Id:RdsResourceId,State:TargetHealth.State,Reason:TargetHealth.Reason}" \
  --output table
```
**What / why:** `State` must be `AVAILABLE` for the promoted instance before you trust worker traffic.

### 4.4 Verify the registration API (ASG) path

The ASG connects directly via the instance endpoint stored in SSM (`/CODE/.../registration.db.url`). Since the endpoint is preserved by switchover, just confirm health:
```bash
# Confirm the SSM URL still points at the preserved endpoint
aws ssm get-parameter \
  --name "/notifications/CODE/mobile-notifications/registration.db.url" \
  --query "Parameter.Value" --output text
```
**What / why:** Ensures the API's JDBC URL still resolves to the promoted instance. No change is normally needed because the endpoint hostname is preserved — but confirm.

### 4.5 Post-cutover SQL maintenance

Connect to the promoted instance (Appendix A):
```sql
-- 1) Rebuild optimizer statistics (major upgrades reset pg_statistic)
ANALYZE VERBOSE;

-- 2) Reindex for collation safety WITHOUT locking the table.
--    Do NOT use "REINDEX DATABASE" — it takes ACCESS EXCLUSIVE locks (outage).
--    Scope to the known secondary index; PK is rebuilt by RDS during upgrade.
REINDEX INDEX CONCURRENTLY registrations.idx_registration_shard_topic;
```
**What / why:**
- `ANALYZE VERBOSE` restores query plans so the harvester/registration queries keep hitting indexes.
- A major-version jump can change OS `glibc` collation ordering, which can corrupt text B-tree ordering. Your PK is `(token, topic)` (both text), so reindexing matters — but use **`CONCURRENTLY`** to avoid blocking live traffic. `REINDEX DATABASE` would lock every table and cause an outage.

### 4.6 Functional end-to-end check

```bash
aws lambda invoke \
  --function-name "mobile-notifications-fakebreakingnews-CODE" \
  --payload '{}' /tmp/fakebreakingnews-code.json
cat /tmp/fakebreakingnews-code.json
```
**What / why:** Exercises the full notification path (workers → proxy → upgraded DB) end-to-end in CODE, mirroring what the existing procedure docs use to validate availability.

**✅ Phase 4 exit criteria:** switchover complete · proxy target `AVAILABLE` on promoted instance · API URL verified · `ANALYZE` + concurrent reindex done · fake breaking news succeeds.

---

## Phase 5 — Reconcile CloudFormation WITHOUT replacement (via Riff-Raff)

> Goal: make `registration-db.yaml` match reality (engine, and storage/class only if changed) while CloudFormation reports `Modify / Replacement=False`.

### 5.1 Confirm CFN's recorded physical ID matches the promoted instance

```bash
aws cloudformation describe-stack-resources \
  --stack-name <CODE_DB_STACK_NAME> \
  --logical-resource-id PrivateRegistrationPostgres13DB \
  --query "StackResources[0].{Physical:PhysicalResourceId,Status:ResourceStatus}" \
  --output table
```
**What / why:** After Blue/Green, the promoted instance normally **retains the original identifier**, so CFN's `PhysicalResourceId` should still match `$DB_INSTANCE_ID`. If it does **not** match, do **not** run a normal update — realign state with `resource import` (5.4) instead, or you risk `DeletionPolicy: Retain` orphaning the real DB and creating an empty one.

### 5.2 Open a PR editing ONLY property values

In `registration-db.yaml`, on the existing `PrivateRegistrationPostgres13DB` resource, change **values only**:
- `EngineVersion: <PG18_VERSION>`
- `StorageType:` / `DBInstanceClass:` **only if you actually changed them**
- Add `DBParameterGroupName: !Ref RegistrationsPg18ParameterGroup`

**Do NOT** rename the logical ID `PrivateRegistrationPostgres13DB` or the `DBInstanceIdentifier` string (even though they say `pg13`).
**What / why:** Renaming the logical ID or physical identifier makes CloudFormation treat it as a **new** resource → **replacement → data loss**. Cosmetic renames come much later, separately.

### 5.3 Generate a ChangeSet and verify no replacement

If deploying via Riff-Raff, it will create the ChangeSet; to inspect equivalently:
```bash
aws cloudformation create-change-set \
  --stack-name <CODE_DB_STACK_NAME> \
  --change-set-name pg18-reconcile-code \
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
  --stack-name <CODE_DB_STACK_NAME> \
  --change-set-name pg18-reconcile-code \
  --query "Changes[].ResourceChange.{LogicalId:LogicalResourceId,Action:Action,Replacement:Replacement}" \
  --output table
```
**What / why:** **The RDS instance row MUST show `Action=Modify` and `Replacement=False`.** If it shows `Replace` or `Add`, **abort** — investigate (usually a changed identifier/logical ID, or CFN state mismatch from 5.1).

### 5.4 (Only if 5.1 mismatched) Import the real instance into state

```bash
# Example skeleton — realign CFN to the actual promoted instance rather than replacing it.
aws cloudformation create-change-set \
  --stack-name <CODE_DB_STACK_NAME> \
  --change-set-name pg18-import-code \
  --change-set-type IMPORT \
  --resources-to-import '[{
      "ResourceType":"AWS::RDS::DBInstance",
      "LogicalResourceId":"PrivateRegistrationPostgres13DB",
      "ResourceIdentifier":{"DBInstanceIdentifier":"'"$DB_INSTANCE_ID"'"}
  }]' \
  --template-body file://registration-db.yaml
```
**What / why:** Re-binds CloudFormation to the live instance by identifier without recreating it — the safe way to fix state drift after a Blue/Green swap that CFN doesn't know about.

### 5.5 Execute via Riff-Raff (CODE)

Merge the PR and let Riff-Raff deploy the reconcile to CODE (do **not** click console "Update Stack" — it will drift against the next GitHub-triggered deploy).

### 5.6 Confirm no drift remains

```bash
DRIFT_ID=$(aws cloudformation detect-stack-drift --stack-name <CODE_DB_STACK_NAME> --query StackDriftDetectionId --output text)
aws cloudformation describe-stack-resource-drifts \
  --stack-name <CODE_DB_STACK_NAME> \
  --query "StackResourceDrifts[?StackResourceDriftStatus!='IN_SYNC'].{Id:LogicalResourceId,Status:StackResourceDriftStatus}" \
  --output table
```
**What / why:** Confirms the template now matches reality. Note `AutoMinorVersionUpgrade: True` can later auto-bump the minor version in the maintenance window and re-introduce drift — decide whether to pin only the major (`"18"`) or accept periodic template bumps.

**✅ Phase 5 exit criteria:** ChangeSet showed `Modify/False` (or import succeeded) · Riff-Raff deploy green · drift status `IN_SYNC`.

---

## Phase 6 — Rollback rehearsal (prove it before PROD)

**Before switchover:** simply delete the Blue/Green deployment; Blue is untouched.
```bash
aws rds delete-blue-green-deployment \
  --blue-green-deployment-identifier "$BG_ID" \
  --delete-target false
```
**What / why:** `--delete-target false` tears down the Blue/Green pairing but you decide the fate of the Green instance; pre-switchover, Blue remains the live DB with no impact.

**After switchover:** the old Blue is retained as `<id>-old`. To roll back, repoint the proxy (and, if ever changed, the SSM URL) back to the retained old instance:
```bash
aws rds register-db-proxy-targets \
  --db-proxy-name "$DB_PROXY_NAME" \
  --db-instance-identifiers "<OLD_BLUE_INSTANCE_ID>"
```
**What / why:** Demonstrates you can restore service on the pre-upgrade instance quickly. Rehearsing this is the whole point — you never want to discover the rollback path during a PROD incident.

---

## Phase 7 — Rehearsal teardown (CODE)

```bash
# If you do not intend to keep the rehearsed CODE DB, clean up the B/G scaffolding:
aws rds delete-blue-green-deployment \
  --blue-green-deployment-identifier "$BG_ID" \
  --delete-target false

# Remove the retained "-old" instance once you're satisfied (enable a final snapshot):
aws rds delete-db-instance \
  --db-instance-identifier "<OLD_BLUE_INSTANCE_ID>" \
  --final-db-snapshot-identifier "registrations-code-preupgrade-final"
```
**What / why:** Cleans up rehearsal artifacts and cost. Always take a final snapshot before deleting anything that ever held data. **Never** delete the promoted CODE instance that CFN now manages.

**Also:** remove `~/.psql_history` on any jump host — it can contain passwords from `CREATE SUBSCRIPTION`-style commands (your existing procedure calls this out).

---

## Timing log (fill in during the rehearsal → informs the PROD window)

| Step | Start | End | Duration | Notes |
|---|---|---|---|---|
| B/G create (3.2) | | | | |
| Reach `AVAILABLE` + lag 0 (3.3) | | | | |
| Switchover (4.2) | | | | write-block duration |
| Proxy repoint healthy (4.3) | | | | **key metric** |
| ANALYZE + reindex (4.5) | | | | |
| CFN reconcile (5.x) | | | | |

---

## Appendix A — psql connection helper (via SSM tunnel)

```bash
# Terminal 1 — open an SSH tunnel to the DB via SSM (pattern from existing procedure docs)
eval $(ssm ssh --tags registration,mobile-notifications,CODE -p mobile --raw --newest) \
  -L 5432:${DB_HOST}:5432

# Terminal 2 — fetch the master password from SSM
aws --profile=mobile --region=eu-west-1 ssm get-parameter --with-decryption \
  --name /notifications/CODE/registrations-db-password | jq -r .Parameter.Value

# Terminal 3 — connect (paste the password when prompted)
psql -h localhost -U root ${DB_NAME}
```
**What / why:** The DB is private (`PubliclyAccessible: False`), so you reach it through an SSM tunnel. To validate the **Green** endpoint pre-cutover (3.4), substitute `<GREEN_INSTANCE_ENDPOINT>` for `${DB_HOST}` in the `-L` forward.

---

## Quick reference — the five things that most often go wrong

1. **Proxy target-swap is manual** (4.3). Blue/Green won't do it; it's what keeps the worker lambdas alive.
2. **PG18 must be supported by RDS Proxy** (0.3) or the whole thing is blocked.
3. **Never rename the CFN logical ID / `DBInstanceIdentifier`** (5.2) → replacement/data loss.
4. **`REINDEX … CONCURRENTLY`, not `REINDEX DATABASE`** (4.5) → avoid an outage.
5. **All CFN changes via PR + Riff-Raff** (Phases 2 & 5), never console/CLI → avoid drift reverts.
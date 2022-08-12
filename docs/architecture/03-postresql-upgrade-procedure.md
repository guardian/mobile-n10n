# Detailed Database Upgrade Procedure

## Preparation: Set up a new empty Postgresql 13 DB instance for registrations database
1. Use the following settings:
- DB instance name: `notifications-registrations-db-private-pg13-prod`
- Instance class: m6g.large
- Multi-AZ
- VPC: notifications (vpc-5dddcb3f)
- Subnet group: notifications-registrations-db-subnet-group-private-prod
- VPC security groups: registrations-db-PROD, NO default
- Storage type: SSD (gp2)
- Password authentication
- Enable auto minor version upgrade
- Parameter group: `logical-replication-subscriber` (which sets `rds.logical_replication` to 1)

2. With the same schemas and user accounts

3. No data

## Step 1: Set up a temporary database to serve breaking news notifications
1. In AWS console, start `Take DB snapshot` action on the current PROD database
- Name: registrations-PROD-upgrade-temp

2. Run `Restore snapshot` from the created snapshot

- DB instance identifier: `notifications-registrations-db-private-prod-temp`
- Multi-AZ
- VPC: notifications (vpc-5dddcb3f)
- Subnet group: notifications-registrations-db-subnet-group-private-prod
- VPC security groups: registrations-db-PROD, NO default
- Instance class: db.t3.medium
- Storage type: SSD (gp2)
- Password authentication
- Disable auto minor version upgrade

3. Run some SQLs to mitigate the effects of [lazy loading](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_RestoreFromSnapshot.html)
- At terminal 1, set up a SSH tunnel to the database with `eval $(ssm ssh --tags registration,mobile-notifications,PROD -p mobile --raw --newest) -L 5432:notifications-registrations-db-private-prod-temp.crwidilr2ofx.eu-west-1.rds.amazonaws.com:5432`
- At terminal 2, get the password with `aws --profile=mobile --region=eu-west-1 ssm get-parameter --with-decryption --name /notifications/PROD/registrations-db-password | jq -r .Parameter.Value`
- At terminal 3, open psql with `psql -h localhost -U root registrationsPROD`
- Run the queries `SELECT COUNT(1) FROM registrations.registrations;`
- Run the queries `SELECT platform, COUNT(1) FROM registrations.registrations GROUP BY platform;`

4. Switch the worker lambda functions over to the temporary database by 
- changing the target group of the proxy `registrations-db-proxy-cdk-prod` to `notifications-registrations-db-private-prod-temp`

5. Trigger the lambda [mobile-notifications-fakebreakingnews-PROD](https://eu-west-1.console.aws.amazon.com/lambda/home?region=eu-west-1#/functions/mobile-notifications-fakebreakingnews-PROD?tab=code) to check if the service is available

## Step 2: Enable logical replication
1. in AWS console, modify the DB `notifications-registrations-db-private-prod` to use the parameter group `logical-replication-publisher`
- The parameter group has the following specific parameter values
- `rds.logical_replication` to 1 (which causes AWS to set `wal_level` to `logical`)
- `max_wal_senders` to 15

2. Reboot the database to make parameters come into effect

3. Switch back from the temporary database
- Change the target group of the proxy `registrations-db-proxy-cdk-prod` to `notifications-registrations-db-private-prod`
- Trigger the lambda [mobile-notifications-fakebreakingnews-PROD](https://eu-west-1.console.aws.amazon.com/lambda/home?region=eu-west-1#/functions/mobile-notifications-fakebreakingnews-PROD?tab=code)

## Step 3: Set up publication in old PROD database
1. Open the SQL client to the old PROD database
- At terminal 1, set up a SSH tunnel to the database with `eval $(ssm ssh --tags registration,mobile-notifications,PROD -p mobile --raw --newest) -L 5432:notifications-registrations-db-private-prod.crwidilr2ofx.eu-west-1.rds.amazonaws.com:5432`
- At terminal 2, get the password with `aws --profile=mobile --region=eu-west-1 ssm get-parameter --with-decryption --name /notifications/PROD/registrations-db-password | jq -r .Parameter.Value`
- At terminal 3, open psql with `psql -h localhost -U root registrationsPROD`

2. Create a publication by running `CREATE PUBLICATION alltables FOR ALL TABLES;` in the psql session
- To check the publication, run `SELECT * FROM pg_publication;`

3. After the new database makes a subscription, we can check the status of logical replication by running `SELECT * FROM pg_replication_slots;`
- There should be an entry with the subscription name
- The `active` column should be `t`
- We may run `SELECT * FROM pg_stat_replication;` to get some statistics

## Step 4: Set up subscription in Postgresql 13 database
1. Open the SQL client to the old PROD database
- At terminal 1, set up a SSH tunnel to the database with `eval $(ssm ssh --tags registration,mobile-notifications,PROD -p mobile --raw --newest) -L 5432:notifications-registrations-db-private-pg13-prod.crwidilr2ofx.eu-west-1.rds.amazonaws.com:5432`
- At terminal 2, get the password with `aws --profile=mobile --region=eu-west-1 ssm get-parameter --with-decryption --name /notifications/PROD/registrations-db-password | jq -r .Parameter.Value`
- At terminal 3, open psql with `psql -h localhost -U root registrationsPROD`

2. Create a subscription by running the following statement in the psql session.  Replace the password placeholder.
```
CREATE SUBSCRIPTION mysub
         CONNECTION 'host=notifications-registrations-db-private-prod.crwidilr2ofx.eu-west-1.rds.amazonaws.com port=5432 user=root dbname=registrationsPROD password=<password>'
        PUBLICATION alltables;
```

3. Check the status of the subscription by running `SELECT srsubid, pg_filenode_relation(0,srrelid), srsublsn, srsubstate FROM pg_subscription_rel;`
- The `srsubstate` shows the current state:
- `i` – Initialize,
- `d` – Data is being copied,
- `s` – Synchronized,
- `r` – Ready (normal replication)

4. When the state is changed to `r` (ready), make sure that the initial data copy has been completed by running `SELECT COUNT(1) FROM registrations.registrations;`

5. After inital data copy, add primary constraint and an index by running:
```
ALTER TABLE ONLY registrations.registrations
    ADD CONSTRAINT registrations_pkey PRIMARY KEY (token, topic);

CREATE INDEX idx_registration_shard_topic ON registrations.registrations USING btree (shard, topic);   
```

6. To make sure that data are being synchronised continuously, we may run the queries in both database and compare.  (Change the timestamp)
```
SELECT COUNT(1) FROM registrations.registrations;

SELECT token, topic, lastmodified FROM registrations.registrations
WHERE lastmodified >= TO_TIMESTAMP('2022-08-11 13:00', 'YYYY-MM-DD HH24:MI') 
ORDER BY lastmodified;
```

7. When we are ready to switch over, update the optimizer statistics by running `ANALYZE VERBOSE`

## Step 5: Stop subscription
1. Stop the subscription in the new Postgresql 13 database by running `DROP SUBSCRIPTION mysub;`

2. In AWS console, modify the DB `notifications-registrations-db-private-pg13-prod` to use the parameter group `default.postgres13`

3. Reboot the DB instance `notifications-registrations-db-private-pg13-prod`

## Step 6: Switch over

1. Switch the registrations API over to the new database by
- changing the parameter in parameter store `/notifications/PROD/mobile-notifications/registration.db.url`
- from: `jdbc:postgresql://notifications-registrations-db-private-prod.crwidilr2ofx.eu-west-1.rds.amazonaws.com/registrationsPROD?currentSchema=registrations`
- to:   `jdbc:postgresql://notifications-registrations-db-private-pg13-prod.crwidilr2ofx.eu-west-1.rds.amazonaws.com/registrationsPROD?currentSchema=registrations`

2. Restart the registrations API by redeploying the main branch of `mobile-n10n:registration` via riffraff

3. Switch the worker lambda functions to the new database by
- Change the target group of the proxy `registrations-db-proxy-cdk-prod` to `notifications-registrations-db-private-pg13-prod`
- Trigger the lambda [mobile-notifications-fakebreakingnews-PROD](https://eu-west-1.console.aws.amazon.com/lambda/home?region=eu-west-1#/functions/mobile-notifications-fakebreakingnews-PROD?tab=code)

## Step 7: Clean up
1. Remove `<HOME>/.psql_history` file as the history contains the password in the `CREATE SUBSCRIPTION` statement

2. Stop the publication in the old PROD database by running `DROP PUBLICATION alltables`

3. Delete the temporary database `notifications-registrations-db-private-prod-temp`

4. When the notifications system has been running well with the new database, we may remove the old PROD database
- Enable `create final snapshot`


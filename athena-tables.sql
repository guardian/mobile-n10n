CREATE EXTERNAL TABLE `raw_events_code`(
`querystring` string COMMENT 'from deserializer',
`event` string COMMENT 'from deserializer',
`datetime` timestamp COMMENT 'from deserializer')
PARTITIONED BY (
`date` string,
`hour` int)
ROW FORMAT SERDE
'org.apache.hive.hcatalog.data.JsonSerDe'
WITH SERDEPROPERTIES (
  'timestamp.formats'='yyyy-MM-dd\'T\'HH:mm:ss')
    STORED AS INPUTFORMAT
    'org.apache.hadoop.mapred.TextInputFormat'
    OUTPUTFORMAT
    'org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat'
    LOCATION
    's3://<CODE_BUCKET>/fastly';


CREATE EXTERNAL TABLE `raw_events_prod`(
`querystring` string COMMENT 'from deserializer',
`event` string COMMENT 'from deserializer',
`datetime` timestamp COMMENT 'from deserializer')
PARTITIONED BY (
`date` string,
`hour` int)
ROW FORMAT SERDE
'org.apache.hive.hcatalog.data.JsonSerDe'
WITH SERDEPROPERTIES (
  'timestamp.formats'='yyyy-MM-dd\'T\'HH:mm:ss')
    STORED AS INPUTFORMAT
    'org.apache.hadoop.mapred.TextInputFormat'
    OUTPUTFORMAT
    'org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat'
    LOCATION
    's3://<PROD_BUCKET>/fastly';


CREATE OR REPLACE VIEW notification_received_prod AS
  SELECT
    "dateTime" "received_timestamp",
    "regexp_extract"("querystring", 'notificationId=([\w-]+)', 1) "notificationId",
    "regexp_extract"("querystring", 'platform=(\w+)', 1) "platform",
    "regexp_extract"("querystring", 'provider=(\w+)', 1) "provider",
    "date" "partition_date",
    "hour" "partition_hour"
  FROM
    notifications.raw_events_prod;


CREATE OR REPLACE VIEW notification_received_code AS
  SELECT
    "dateTime" "received_timestamp",
    "regexp_extract"("querystring", 'notificationId=([\w-]+)', 1) "notificationId",
    "regexp_extract"("querystring", 'platform=(\w+)', 1) "platform",
    "regexp_extract"("querystring", 'provider=(\w+)', 1) "provider",
    "date" "partition_date",
    "hour" "partition_hour"
  FROM
    notifications.raw_events_code;

CREATE OR REPLACE VIEW notification_received_72h_prod AS
  SELECT
    "dateTime" "received_timestamp",
    "regexp_extract"("querystring", 'notificationId=([\w-]+)', 1) "notificationId",
    "regexp_extract"("querystring", 'platform=(\w+)', 1) "platform",
    "regexp_extract"("querystring", 'provider=(\w+)', 1) "provider",
    "date" "partition_date",
    "hour" "partition_hour"
  FROM
    notifications.raw_events_prod
  WHERE ("from_iso8601_date"("date") >= ("from_iso8601_date"("date") - INTERVAL  '3' DAY));


CREATE OR REPLACE VIEW notification_received_72h_code AS
  SELECT
    "dateTime" "received_timestamp",
    "regexp_extract"("querystring", 'notificationId=([\w-]+)', 1) "notificationId",
    "regexp_extract"("querystring", 'platform=(\w+)', 1) "platform",
    "regexp_extract"("querystring", 'provider=(\w+)', 1) "provider",
    "date" "partition_date",
    "hour" "partition_hour"
  FROM
    notifications.raw_events_code
  WHERE ("from_iso8601_date"("date") >= ("from_iso8601_date"("date") - INTERVAL  '3' DAY));
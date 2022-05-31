# Reports

The Reports API is used by the [Ophan Dashboard](https://dashboard.ophan.co.uk/notifications) to show data about when notifications were sent and the number of deliveries per platform.

## Reporting Flow

Below is a flow diagram showing the actors and interactions required to report on sent notifications.

There is more going on than just what's described in the diagram (specifically around how notification data is ingested into the ophan data lake). However, this diagram should cover the components most relevant to mobile people.

```mermaid
sequenceDiagram
    participant notif as Notification Source
    participant notification as Notification (API)
    participant dynamo as DynamoDB (mobile-notifications-reports)
    participant harvesterQueue as Harvester SQS
    participant harvester as Harvester (queue poller)
    participant worker as Workers (lambdas)
    participant device as Device
    participant fastly as Fastly
    participant s3 as S3
    participant lambda as Event Consumer
    participant reportextractor as Report Extractor (cron lambda)
    participant report as Report (API)
    participant dashboard as Ophan Dashboard (https://dashboard.ophan.co.uk/notifications)
    notif->>notification: send push notification
    notification->>dynamo: get notification by id
    notification->>notification: check if duplicate
    alt is duplicate
        notification->>notif: no notification sent
    else is not duplicate
        notification->>notification: define new notifcation record
        notification->>dynamo: create new notification record
    end
    note over dynamo: Initial record in dynamo has empty list of reports & events
    notification->>harvesterQueue: Send batches of notification send requests
    harvester->>harvesterQueue: Get notification batches to send
    harvester->>worker: Trigger send to devices
    worker->>device: Push notification (via firebase/apns)
    notification->>notification: Get total registration count
    notification->>notification: Create a SenderResult (for batches successfully sent to harvester)
    notification->>dynamo: Update notification record
    note over dynamo: Updated record includes SenderResult as an item in "reports" array
    device->>fastly: make notification/received api call
    fastly->>s3: write log data to s3 (mobile event logs fastly)
    lambda->>lambda: AthenaLambda runs on schedule
    lambda->>s3: get number of notification/received events
    lambda->>s3: write parsed data (mobile event logs athena)
    lambda->>dynamo: update notification "events" field with platform received count
    dashboard->>dashboard: page load
    dashboard->>report: request reports by date range
    report->>dynamo: query notifications table
    dashboard->>dashboard: render notification data
```
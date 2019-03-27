#!/usr/bin/env bash

mkdir -p lib/common
cp -r common/* lib/common

mkdir -p lib/common-event-consumer
cp -r commoneventconsumer/* lib/common-event-consumer

mkdir -p lib/common-schedule-dynamodb
cp -r commonscheduledynamodb/* lib/common-schedule-dynamodb

mkdir -p lib/common-test
cp -r commontest/* lib/common-test

mkdir -p app/db-cleaner
cp -r db-cleaner/* app/db-cleaner

mkdir -p lib/event-consumer
cp -r eventconsumer/* lib/event-consumer

mkdir -p app/fake-breaking-news
cp -r fakebreakingnewslambda/* app/fake-breaking-news

mkdir -p app/notification
cp -r notification/* app/notification

mkdir -p app/notification-workers
cp -r notificationworkerlambda/* app/notification-workers

mkdir -p app/registration
cp -r registration/* app/registration

mkdir -p app/report
cp -r report/* app/report

mkdir -p app/schedule-lambda
cp -r schedulelambda/* app/schedule-lambda

mkdir -p lib/api-client
cp -r api-client/* lib/api-client

cp build.sbt.post-migration build.sbt

rm -rf common commoneventconsumer commonscheduledynamodb commontest db-cleaner eventconsumer fakebreakingnewslambda notification notificationworkerlambda registration report schedulelambda api-client
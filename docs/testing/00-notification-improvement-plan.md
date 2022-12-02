# Plan for how to improve the performance of the notification delivery times

We have a high-level SLO set by editorial:

"90% of our readers receive notifications within 2 minutes"

This document highlights the ideas and initiatives towards reaching this SLO.

## Background

We already have a system in place, we're going to start by trying to fine-tune what we have. We aim to make some improvements to the performance in doing so, but don't predict that this alone will help us reach our SLO.

After we've explored, tested and possibly implemented any successful fine-tuning we could consider larger pieces of work that would likely have more significant improvements. These could take the form of product improvements or larger refactoring/re-architecting.

## Fine-tuning

The fine-tuning phase will involve a test and evaluation cycle in CODE for each individual change. To support this we've replicated the production registrations DB in CODE meaning we can replicate production performance of the harvester.

Current ideas and initiatives for the fine-tuning phase:

- [Increasing shard size](./01-shard-size.md)
- [RDS proxy](./02-rds-proxy.md)
- Aurora and postgres 14
- [Query performance improvements ](../architecture/02-database-tuning.md)
- ~~Handling DB connection errors more gracefully~~ parked for now since errors reduced after DB vacuum (including re-indexing)
- [Lambda concurrency](./04-reducing-lambda-duration.md)
- [Reducing lambda execution time](./09-reducing-lambda-cold-start-times.md)
- Change batch size for sender lambdas (increase throughput to apns/fcm)
- Increase sqs throughput: use multiple threads, validate we're using batch requests

## Product improvements

Ideas for potential product improvements that would likely require some integration with the breaking news tool:

- VIP lanes: rather than have one system servicing all our alerts, have four dedicated stacks for all high volume alerts, one for each edition.
- Pre-warmer: warm up the stacks and get them ready for launch. Sync with tools and send a pre-cache signal to ensure the n10n stack is ready and primed for her-maj-dies like alerts.

## Re-architect

Current ideas for re-architecting:

- [Microsplinters](https://github.com/itsibitzi/n10n-poc/blob/main/n10n-broker/src/main.rs): preshard the DB and use these for querying
- Using Go to handle concurrency
- Using ZIO to handle concurrency
- Change lambda runtime: there are [studies](https://filia-aleks.medium.com/aws-lambda-battle-2021-performance-comparison-for-all-languages-c1b441005fd1) demonstrating that jvm-based lambdas are the worst performing, we could get benefits from migrating to typescript lambdas (or go, rust)
- Pre-cache breaking news tokens: create a cron job that queries db to grab tokens for our breaking news topics to avoid these queries being executed as part of our send flow. Different flow for breaking news notifications: when requested we skip the harvester and immediately populate the sender queues with tokens to send.

import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { SenderWorkerStack } from '../lib/senderworker';

const app = new cdk.App()

new SenderWorkerStack(app, 'SenderWorkerStackCODE', {
  stack: "mobile-notifications-workers",
  stage: "CODE"
})

new SenderWorkerStack(app, 'SenderWorkerStackPROD', {
  stack: "mobile-notifications-workers",
  stage: "PROD"
})

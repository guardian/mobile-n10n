import 'source-map-support/register';
import {App} from 'aws-cdk-lib'
import { SenderWorkerStack } from '../lib/senderworker';

const app = new App()

new SenderWorkerStack(app, 'SenderWorkerStack-CODE', {
  stack: "mobile-notifications-workers",
  stage: "CODE"
})

new SenderWorkerStack(app, 'SenderWorkerStack-PROD', {
  stack: "mobile-notifications-workers",
  stage: "PROD"
})

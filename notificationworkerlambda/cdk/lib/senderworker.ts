import * as cdk from '@aws-cdk/core'
import * as sqs from '@aws-cdk/aws-sqs'
import * as iam from '@aws-cdk/aws-iam'
import { GuStack } from "@guardian/cdk/lib/constructs/core"
import type { App } from "@aws-cdk/core"
import type { GuStackProps } from "@guardian/cdk/lib/constructs/core"

class SenderWorker extends cdk.Construct {
  constructor(scope: GuStack, id: string) {
    super(scope, id)

    cdk.Tags.of(this).add("App", id)

    const senderDlq = new sqs.Queue(this, 'SenderDlq')
    const senderSqs = new sqs.Queue(this, 'SenderSqs', {
      visibilityTimeout: cdk.Duration.seconds(100),
      retentionPeriod: cdk.Duration.hours(1),
      deadLetterQueue: {
        queue: senderDlq,
        maxReceiveCount: 5
      }
    })

    const executionRole = new iam.Role(this, 'ExecutionRole', {
      assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
      path: "/",
      inlinePolicies: {
        logs: new iam.PolicyDocument({
          statements: [
            new iam.PolicyStatement({
              actions: [ 'logs:CreateLogGroup' ],
              resources: [ `arn:aws:logs:eu-west-1:${scope.account}:*` ]
            }),
            new iam.PolicyStatement({
              actions: [ 'logs:CreateLogStream', 'logs:PutLogEvents' ],
              resources: [ `arn:aws:logs:eu-west-1:${scope.account}:log-group:/aws/lambda/*:*` ]
            })
          ] }),
      }
    })
  }
}

export class SenderWorkerStack extends GuStack {
  constructor(scope: App, id: string, props: GuStackProps) {
    super(scope, id, props);

    new SenderWorker(this, "ios-worker")
    new SenderWorker(this, "android-worker")

  }


}

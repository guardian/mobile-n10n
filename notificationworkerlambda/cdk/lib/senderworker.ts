import * as cdk from '@aws-cdk/core'
import * as ecr from '@aws-cdk/aws-ecr'
import * as sqs from '@aws-cdk/aws-sqs'
import * as iam from '@aws-cdk/aws-iam'
import * as lambda from '@aws-cdk/aws-lambda'
import { GuStack } from "@guardian/cdk/lib/constructs/core"
import type { App } from "@aws-cdk/core"
import type { GuStackProps } from "@guardian/cdk/lib/constructs/core"

type SenderWorkerOpts = {
  handler: string,
  imageRepo: ecr.IRepository,
  buildId: string
}

class SenderWorker extends cdk.Construct {
  constructor(scope: GuStack, id: string, opts: SenderWorkerOpts) {
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

    const cleanerQueueArnParam = new cdk.CfnParameter(this, "CleanerQueueArnParam", {
      type: "String",
      description: "The ARN of the cleaner SQS queue"
    });

    const platformParam = new cdk.CfnParameter(this, "Platform", {
      type: "String",
      description: "The platform handled by this worker",
      allowedValues: [
        "android",
        "android-beta",
        "ios",
        "android-edition",
        "ios-edition"
      ]
    });

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
        SQSOutput: new iam.PolicyDocument({
          statements: [
            new iam.PolicyStatement({
              actions: [ 'sqs:*' ],
              resources: [ senderSqs.queueArn ]
            })
          ] }),
        SQSInput: new iam.PolicyDocument({
          statements: [
            new iam.PolicyStatement({
              actions: [ 'sqs:SendMessage' ],
              resources: [ cleanerQueueArnParam.valueAsString ]
            })
          ] }),
        Conf: new iam.PolicyDocument({
          statements: [
            new iam.PolicyStatement({
              actions: [ 'ssm:GetParametersByPath' ],
              resources: [ `arn:aws:ssm:${scope.region}:${scope.account}:parameter/notifications/${scope.stage}/workers/${platformParam.valueAsString}` ]
            })
          ] }),
        Cloudwatch: new iam.PolicyDocument({
          statements: [
            new iam.PolicyStatement({
              actions: [ 'cloudwatch:PutMetricData' ],
              resources: [ '*' ]
            })
          ] }),
        }
    })

    const codeImage = lambda.DockerImageCode.fromEcr(opts.imageRepo, {
      cmd: [ opts.handler ],
      tag: opts.buildId
    })

    const senderLambdaCtr = new lambda.DockerImageFunction(this, 'SenderLambdaCtr', {
      functionName: `${scope.stack}-${id}-sender-ctr-${scope.stage}`,
      code: codeImage
    })
  }
}

export class SenderWorkerStack extends GuStack {
  constructor(scope: App, id: string, props: GuStackProps) {
    super(scope, id, props)

    const buildIdParam = new cdk.CfnParameter(this, "BuildId", {
      type: "String",
      description: "build id from teamcity, the image should be tagged with this"
    });

    const notificationEcrRepo =
      ecr.Repository.fromRepositoryAttributes(this, "NotificationLambdaRepository", {
        repositoryArn: cdk.Fn.importValue("NotificationLambdaRepositoryArn"),
        repositoryName: cdk.Fn.importValue("NotificationLambdaRepositoryName")
      })

    new SenderWorker(this, "ios-worker", {
      handler: "com.gu.notifications.worker.AndroidSender::handleChunkTokens",
      imageRepo: notificationEcrRepo,
      buildId: buildIdParam.valueAsString
    })

    // new SenderWorker(this, "android-worker")

  }


}

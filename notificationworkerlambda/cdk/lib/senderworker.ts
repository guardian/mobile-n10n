import * as cdk from '@aws-cdk/core'
import * as ecr from '@aws-cdk/aws-ecr'
import * as sqs from '@aws-cdk/aws-sqs'
import * as sns from '@aws-cdk/aws-sns'
import * as iam from '@aws-cdk/aws-iam'
import * as lambda from '@aws-cdk/aws-lambda'
import * as cloudwatch from '@aws-cdk/aws-cloudwatch'
import * as ssm from '@aws-cdk/aws-ssm'

import {SnsAction} from '@aws-cdk/aws-cloudwatch-actions'
import { GuStack } from "@guardian/cdk/lib/constructs/core"
import type { App } from "@aws-cdk/core"
import type { GuStackProps } from "@guardian/cdk/lib/constructs/core"

type SenderWorkerOpts = {
  handler: string,
  imageRepo: ecr.IRepository,
  buildId: string,
  reservedConcurrency: number,
  alarmTopic: sns.ITopic,
  tooFewInvocationsAlarmPeriod: cdk.Duration,
  tooFewInvocationsEnabled: boolean,
  cleanerQueueArn: string,
  platform: string
}

class SenderWorker extends cdk.Construct {

  readonly senderSqs: sqs.Queue

  constructor(scope: GuStack, id: string, opts: SenderWorkerOpts) {
    super(scope, id)

    cdk.Tags.of(this).add("App", id)

    const snsTopicAction = new SnsAction(opts.alarmTopic)

    const senderDlq = new sqs.Queue(this, 'SenderDlq')
    this.senderSqs = new sqs.Queue(this, 'SenderSqs', {
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
        SQSOutput: new iam.PolicyDocument({
          statements: [
            new iam.PolicyStatement({
              actions: [ 'sqs:*' ],
              resources: [ this.senderSqs.queueArn ]
            })
          ] }),
        SQSInput: new iam.PolicyDocument({
          statements: [
            new iam.PolicyStatement({
              actions: [ 'sqs:SendMessage' ],
              resources: [ opts.cleanerQueueArn ]
            })
          ] }),
        Conf: new iam.PolicyDocument({
          statements: [
            new iam.PolicyStatement({
              actions: [ 'ssm:GetParametersByPath' ],
              resources: [ `arn:aws:ssm:${scope.region}:${scope.account}:parameter/notifications/${scope.stage}/workers/${opts.platform}` ]
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
      code: codeImage,
      environment: {
        Stage: scope.stage,
        Stack: scope.stack,
        App: id,
        Platform: opts.platform
      },
      memorySize: 3008,
      description: `sends notifications for ${id}`,
      role: executionRole,
      timeout: cdk.Duration.seconds(90),
      reservedConcurrentExecutions: opts.reservedConcurrency
    })

    const senderSqsEventSourceMapping = new lambda.EventSourceMapping(this, "SenderSqsEventSourceMapping", {
      batchSize: 1,
      enabled: true,
      eventSourceArn: this.senderSqs.queueArn,
      target: senderLambdaCtr
    })
    senderSqsEventSourceMapping.node.addDependency(this.senderSqs)
    senderSqsEventSourceMapping.node.addDependency(senderLambdaCtr)

    const senderThrottleAlarm = new cloudwatch.Alarm(this, 'SenderThrottleAlarm', {
      alarmDescription: `Triggers if the ${id} sender lambda is throttled in ${scope.stage}.`,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      evaluationPeriods: 1,
      threshold: 0,
      metric: senderLambdaCtr.metricThrottles({period: cdk.Duration.seconds(360)}),
      statistic: "Sum",
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING
    })
    senderThrottleAlarm.addAlarmAction(snsTopicAction)
    senderThrottleAlarm.addOkAction(snsTopicAction)

    const senderErrorAlarm = new cloudwatch.Alarm(this, 'SenderErrorAlarm', {
      alarmDescription: `Triggers if the ${id} sender lambda errors in ${scope.stage}.`,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      evaluationPeriods: 1,
      threshold: 0,
      metric: senderLambdaCtr.metricErrors({period: cdk.Duration.seconds(360)}),
      statistic: "Sum",
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING
    })
    senderErrorAlarm.addAlarmAction(snsTopicAction)
    senderErrorAlarm.addOkAction(snsTopicAction)

    const senderTooFewInvocationsAlarm = new cloudwatch.Alarm(this, 'SenderTooFewInvocationsAlarm', {
      alarmDescription: `Triggers if the ${id} sender lambda is not frequently invoked in ${scope.stage}.`,
      comparisonOperator: cloudwatch.ComparisonOperator.LESS_THAN_OR_EQUAL_TO_THRESHOLD,
      evaluationPeriods: 1,
      threshold: 0,
      metric: senderLambdaCtr.metricInvocations({period: cdk.Duration.seconds(360)}),
      statistic: "Sum",
      treatMissingData: cloudwatch.TreatMissingData.BREACHING,
      actionsEnabled: false // isEnabled
    })
    senderTooFewInvocationsAlarm.addAlarmAction(snsTopicAction)
    senderTooFewInvocationsAlarm.addOkAction(snsTopicAction)

    // this advertises the name of the sender queue to the harvester app
    new ssm.StringParameter(this, 'SenderQueueSSMParameter', {
      parameterName: `/notifications/${scope.stage}/workers/harvester/${id}LiveSqsUrl`,
      simpleName: false,
      stringValue: this.senderSqs.queueUrl,
      tier: ssm.ParameterTier.STANDARD,
      dataType: ssm.ParameterDataType.TEXT
    })
  }
}

export class SenderWorkerStack extends GuStack {
  constructor(scope: App, id: string, props: GuStackProps) {
    super(scope, id, props)

    const senderTooFewInvocationsAlarmPeriodParam = new cdk.CfnParameter(this, "SenderTooFewInvocationsAlarmPeriodParam", {
      type: "Number",
      description: "How long until no execution is suspicious, in seconds"
    })

    const reservedConcurrencyParam = new cdk.CfnParameter(this, "ReservedConcurrency", {
      type: "Number",
      description: "How many concurrent execution to provision the lamdba with"
    })

    const buildIdParam = new cdk.CfnParameter(this, "BuildId", {
      type: "String",
      description: "build id from teamcity, the image should be tagged with this"
    })

    const alarmTopicArnParam = new cdk.CfnParameter(this, "AlarmTopicArn", {
      type: "String",
      description: "The ARN of the SNS topic to send all the cloudwatch alarms to"
    })

    const cleanerQueueArnParam = new cdk.CfnParameter(this, "CleanerQueueArnParam", {
      type: "String",
      description: "The ARN of the cleaner SQS queue"
    });

    const notificationEcrRepo =
      ecr.Repository.fromRepositoryAttributes(this, "NotificationLambdaRepository", {
        repositoryArn: cdk.Fn.importValue("NotificationLambdaRepositoryArn"),
        repositoryName: cdk.Fn.importValue("NotificationLambdaRepositoryName")
      })

    const isEnabled = this.withStageDependentValue({
      app: id,
      variableName: "actionsEnabled",
      stageValues: {
        CODE: false,
        PROD: true
      }
    })

    let sharedOpts = {
      imageRepo: notificationEcrRepo,
      buildId: buildIdParam.valueAsString,
      reservedConcurrency: reservedConcurrencyParam.valueAsNumber,
      alarmTopic: sns.Topic.fromTopicArn(this, 'AlarmTopic', alarmTopicArnParam.valueAsString),
      tooFewInvocationsAlarmPeriod: cdk.Duration.seconds(senderTooFewInvocationsAlarmPeriodParam.valueAsNumber),
      tooFewInvocationsEnabled: isEnabled,
      cleanerQueueArn: cleanerQueueArnParam.valueAsString
    }

    let senderWorkers: Record<string, string> = {
      "ios": "com.gu.notifications.worker.IOSSender::handleChunkTokens",
      "android": "com.gu.notifications.worker.AndroidSender::handleChunkTokens"
    }

    let workerQueueArns: string[] = []

    for(let workerName in senderWorkers) {
      let worker = new SenderWorker(this, workerName, {
        platform: workerName,
        handler: senderWorkers[workerName],
        ...sharedOpts
      })
      workerQueueArns.push(worker.senderSqs.queueArn)
    }

    this.exportValue(cdk.Fn.join(",", workerQueueArns), { name: "NotificationSenderWorkerQueueArns" })
  }
}

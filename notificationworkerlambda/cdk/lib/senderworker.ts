import * as cdk from 'aws-cdk-lib'
import { Construct } from "constructs";
import { aws_ecr as ecr } from 'aws-cdk-lib'
import { aws_sqs as sqs } from 'aws-cdk-lib'
import { aws_sns as sns } from 'aws-cdk-lib'
import { aws_iam as iam } from 'aws-cdk-lib'
import { aws_lambda as lambda } from 'aws-cdk-lib'
import { aws_cloudwatch as cloudwatch } from 'aws-cdk-lib'
import { aws_ssm as ssm } from 'aws-cdk-lib'

import {SnsAction} from 'aws-cdk-lib/aws-cloudwatch-actions'
import { GuStack } from "@guardian/cdk/lib/constructs/core"
import type { App } from "aws-cdk-lib"
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
  platform: string,
  paramPrefix: string
}

class SenderWorker extends Construct {

  readonly senderSqs: sqs.Queue

  constructor(scope: GuStack, id: string, opts: SenderWorkerOpts) {
    super(scope, id)

    cdk.Tags.of(this).add("App", id)

    const provisionedConcurrency = 50

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

    const version = senderLambdaCtr.currentVersion
    const alias = new lambda.Alias(this,'SenderLambdaCtrAlias', {
      aliasName: 'Live',
      version,
      provisionedConcurrentExecutions: provisionedConcurrency
    })

    const senderSqsEventSourceMapping = new lambda.EventSourceMapping(this, "SenderSqsEventSourceMapping", {
      batchSize: 1,
      enabled: true,
      eventSourceArn: this.senderSqs.queueArn,
      target: alias
    })
    senderSqsEventSourceMapping.node.addDependency(this.senderSqs)
    senderSqsEventSourceMapping.node.addDependency(alias)

    const senderThrottleAlarm = new cloudwatch.Alarm(this, 'SenderThrottleAlarm', {
      alarmDescription: `Triggers if the ${id} sender lambda is throttled in ${scope.stage}.`,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      evaluationPeriods: 1,
      threshold: 0,
      metric: alias.metricThrottles({period: cdk.Duration.seconds(360)}),
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING
    })
    senderThrottleAlarm.addAlarmAction(snsTopicAction)
    senderThrottleAlarm.addOkAction(snsTopicAction)

    const senderErrorAlarm = new cloudwatch.Alarm(this, 'SenderErrorAlarm', {
      alarmDescription: `Triggers if the ${id} sender lambda errors in ${scope.stage}.`,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      evaluationPeriods: 1,
      threshold: 0,
      metric: alias.metricErrors({period: cdk.Duration.seconds(360)}),
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING
    })
    senderErrorAlarm.addAlarmAction(snsTopicAction)
    senderErrorAlarm.addOkAction(snsTopicAction)

    const senderTooFewInvocationsAlarm = new cloudwatch.Alarm(this, 'SenderTooFewInvocationsAlarm', {
      alarmDescription: `Triggers if the ${id} sender lambda is not frequently invoked in ${scope.stage}.`,
      comparisonOperator: cloudwatch.ComparisonOperator.LESS_THAN_OR_EQUAL_TO_THRESHOLD,
      evaluationPeriods: 1,
      threshold: 0,
      metric: alias.metricInvocations({period: cdk.Duration.seconds(360)}),
      treatMissingData: cloudwatch.TreatMissingData.BREACHING,
      actionsEnabled: false // isEnabled
    })
    senderTooFewInvocationsAlarm.addAlarmAction(snsTopicAction)
    senderTooFewInvocationsAlarm.addOkAction(snsTopicAction)

    // this advertises the name of the sender queue to the harvester app
    new ssm.StringParameter(this, 'SenderQueueSSMParameter', {
      parameterName: `/notifications/${scope.stage}/workers/harvester/${opts.paramPrefix}SqsCdkUrl`,
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
      description: "How many concurrent execution to provision the lambda with"
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

    let sharedOpts = {
      imageRepo: notificationEcrRepo,
      buildId: buildIdParam.valueAsString,
      reservedConcurrency: reservedConcurrencyParam.valueAsNumber,
      alarmTopic: sns.Topic.fromTopicArn(this, 'AlarmTopic', alarmTopicArnParam.valueAsString),
      tooFewInvocationsAlarmPeriod: cdk.Duration.seconds(senderTooFewInvocationsAlarmPeriodParam.valueAsNumber),
      tooFewInvocationsEnabled: props.stage != "CODE",
      cleanerQueueArn: cleanerQueueArnParam.valueAsString
    }

    let workerQueueArns: string[] = []

    const addWorker = (workerName: string, paramPrefix: string, handler: string) => {
      let worker = new SenderWorker(this, workerName, {
        platform: workerName,
        paramPrefix: paramPrefix,
        handler: handler,
        ...sharedOpts
      })
      workerQueueArns.push(worker.senderSqs.queueArn)
    }

    /*
     * add each of the worker lambdas, where each one handles a different
     * platform or app by talking to a different lambda handler function
     */

    addWorker("ios", "iosLive", "com.gu.notifications.worker.IOSSender::handleChunkTokens")
    addWorker("android", "androidLive", "com.gu.notifications.worker.AndroidSender::handleChunkTokens")
    addWorker("ios-edition", "iosEdition", "com.gu.notifications.worker.IOSSender::handleChunkTokens")
    addWorker("android-edition", "androidEdition", "com.gu.notifications.worker.AndroidSender::handleChunkTokens")
    addWorker("android-beta", "androidBeta", "com.gu.notifications.worker.AndroidSender::handleChunkTokens")

    /*
     * each worker has been assigned an SQS queue which, when written to, will
     * trigger it to send its notifications. Here, we export the list of worker
     * queue ARNs so that it can be used in other stacks, for example, Harvester
     * needs to give itself permission to write to these queues.
     */
    new cdk.CfnOutput(this, "NotificationSenderWorkerQueueArns", {
      exportName: "NotificationSenderWorkerQueueArns-" + this.stage,
      value: cdk.Fn.join(",", workerQueueArns)
    })
  }
}

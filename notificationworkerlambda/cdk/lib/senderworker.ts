import * as cdk from 'aws-cdk-lib'
import * as ecr from 'aws-cdk-lib/aws-ecr'
import * as sqs from 'aws-cdk-lib/aws-sqs'
import * as sns from 'aws-cdk-lib/aws-sns'
import * as iam from 'aws-cdk-lib/aws-iam'
import * as lambda from 'aws-cdk-lib/aws-lambda'
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch'
import * as ssm from 'aws-cdk-lib/aws-ssm'
import * as cdkcore from 'constructs'

import {SnsAction} from 'aws-cdk-lib/aws-cloudwatch-actions'
import { GuStack } from "@guardian/cdk/lib/constructs/core"
import type { App } from "aws-cdk-lib"
import type { GuStackProps } from "@guardian/cdk/lib/constructs/core"
import { Rule, RuleTargetInput, Schedule } from 'aws-cdk-lib/aws-events'
import { LambdaFunction } from 'aws-cdk-lib/aws-events-targets'

interface SenderWorkerOpts {
  handler: string,
  imageRepo: ecr.IRepository,
  buildId: string,
  reservedConcurrency: number,
  alarmTopic: sns.ITopic,
  tooFewInvocationsAlarmPeriod: cdk.Duration,
  tooFewInvocationsEnabled: boolean,
  cleanerQueueArn: string,
  platform: string,
  paramPrefix: string,
  isBatchingSqsMessages: boolean,
}

class SenderWorker extends cdkcore.Construct  {

  readonly senderSqs: sqs.Queue

  constructor(scope: GuStack, id: string, props: SenderWorkerOpts) {
    super(scope, id)

    cdk.Tags.of(this).add("App", id)

    const snsTopicAction = new SnsAction(props.alarmTopic)

    const senderDlq = new sqs.Queue(this, 'SenderDlq')
    this.senderSqs = new sqs.Queue(this, 'SenderSqs', {
      visibilityTimeout: props.isBatchingSqsMessages ? cdk.Duration.seconds(190) : cdk.Duration.seconds(100),
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
              resources: [ props.cleanerQueueArn ]
            })
          ] }),
        Conf: new iam.PolicyDocument({
          statements: [
            new iam.PolicyStatement({
              actions: [ 'ssm:GetParametersByPath' ],
              resources: [ `arn:aws:ssm:${scope.region}:${scope.account}:parameter/notifications/${scope.stage}/workers/${props.platform}` ]
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

    const codeImage = lambda.DockerImageCode.fromEcr(props.imageRepo, {
      cmd: [ props.handler ],
      tag: props.buildId
    })

    const senderLambdaCtr = new lambda.DockerImageFunction(this, 'SenderLambdaCtr', {
      functionName: `${scope.stack}-${id}-sender-ctr-${scope.stage}`,
      code: codeImage,
      environment: {
        Stage: scope.stage,
        Stack: scope.stack,
        App: id,
        Platform: props.platform
      },
      memorySize: 10240,
      description: `sends notifications for ${id}`,
      role: executionRole,
      timeout: props.isBatchingSqsMessages ? cdk.Duration.seconds(180) : cdk.Duration.seconds(90),
      reservedConcurrentExecutions: props.reservedConcurrency
    })

    const eventRule = new Rule(this, "SqsMessageSentRule", {
      enabled: true,
      description: `SQS message sent to SQS queue for ${props.platform} on ${scope.stage}`,
      ruleName: `${scope.stack}-sender-${props.platform}-${scope.stage}-trigger`,
      eventPattern: {
        source: ["com.gu.notifications.worker.Harvester"],
        detailType: ["Tokens sent"],
        detail: {
          platform: [`${props.platform}`],
        }
      },
    });
    eventRule.addTarget(new LambdaFunction(senderLambdaCtr, {
      maxEventAge: cdk.Duration.minutes(15), // Optional: set the maxEventAge retry policy
      retryAttempts: 5, // Optional: set the max number of retry attempts
    }));

    const scheduledEventRule = new Rule(this, "ScheduledRerunRule", {
      schedule: Schedule.cron({ minute: "0/10" }),
    });
    scheduledEventRule.addTarget(new LambdaFunction(senderLambdaCtr, {
      event: RuleTargetInput.fromObject({
        source: "com.gu.notifications.worker",
        "detail-type": "Scheduled sender",
        resources: [`${this.senderSqs.queueUrl}`],
        detail: {
          platform: `${props.platform}`,
          batchsize: 10,
        }
      })
    }));

    const senderThrottleAlarm = new cloudwatch.Alarm(this, 'SenderThrottleAlarm', {
      alarmDescription: `Triggers if the ${id} sender lambda is throttled in ${scope.stage}.`,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      evaluationPeriods: 1,
      threshold: 0,
      metric: senderLambdaCtr.metricThrottles({period: cdk.Duration.seconds(360), statistic: "Sum"}),
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING
    })
    senderThrottleAlarm.addAlarmAction(snsTopicAction)
    senderThrottleAlarm.addOkAction(snsTopicAction)

    const senderErrorAlarm = new cloudwatch.Alarm(this, 'SenderErrorAlarm', {
      alarmDescription: `Triggers if the ${id} sender lambda errors in ${scope.stage}.`,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
      evaluationPeriods: 1,
      threshold: 0,
      metric: senderLambdaCtr.metricErrors({period: cdk.Duration.seconds(360), statistic: "Sum"}),
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING
    })
    senderErrorAlarm.addAlarmAction(snsTopicAction)
    senderErrorAlarm.addOkAction(snsTopicAction)

    const senderTooFewInvocationsAlarm = new cloudwatch.Alarm(this, 'SenderTooFewInvocationsAlarm', {
      alarmDescription: `Triggers if the ${id} sender lambda is not frequently invoked in ${scope.stage}.`,
      comparisonOperator: cloudwatch.ComparisonOperator.LESS_THAN_OR_EQUAL_TO_THRESHOLD,
      evaluationPeriods: 1,
      threshold: 0,
      metric: senderLambdaCtr.metricInvocations({period: cdk.Duration.seconds(360), statistic: "Sum"}),
      treatMissingData: cloudwatch.TreatMissingData.BREACHING,
      actionsEnabled: false // isEnabled
    })
    senderTooFewInvocationsAlarm.addAlarmAction(snsTopicAction)
    senderTooFewInvocationsAlarm.addOkAction(snsTopicAction)

    // this advertises the name of the sender queue to the harvester app
    new ssm.StringParameter(this, 'SenderQueueSSMParameter', {
      parameterName: `/notifications/${scope.stage}/workers/harvester/${props.paramPrefix}SqsCdkUrl`,
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

    let sharedOpts = {
      imageRepo: notificationEcrRepo,
      buildId: buildIdParam.valueAsString,
      reservedConcurrency: reservedConcurrencyParam.valueAsNumber,
      alarmTopic: sns.Topic.fromTopicArn(this, 'AlarmTopic', alarmTopicArnParam.valueAsString),
      tooFewInvocationsAlarmPeriod: cdk.Duration.seconds(senderTooFewInvocationsAlarmPeriodParam.valueAsNumber),
      tooFewInvocationsEnabled: props.stage === 'PROD',
      cleanerQueueArn: cleanerQueueArnParam.valueAsString
    }

    let workerQueueArns: string[] = []

    const addWorker = (workerName: string, paramPrefix: string, handler: string, isBatchingSqsMessages: boolean = false) => {
      let worker = new SenderWorker(this, workerName, {
        ...props,
        platform: workerName,
        paramPrefix: paramPrefix,
        handler: handler,
        isBatchingSqsMessages,
        ...sharedOpts
      })
      workerQueueArns.push(worker.senderSqs.queueArn)
    }

    /*
     * add each of the worker lambdas, where each one handles a different
     * platform or app by talking to a different lambda handler function
     */

    addWorker("ios", "iosLive", "com.gu.notifications.worker.IOSSender::handleRequest")
    addWorker("android", "androidLive", "com.gu.notifications.worker.AndroidSender::handleRequest", true)
    addWorker("ios-edition", "iosEdition", "com.gu.notifications.worker.IOSSender::handleRequest")
    addWorker("android-edition", "androidEdition", "com.gu.notifications.worker.AndroidSender::handleRequest")
    addWorker("android-beta", "androidBeta", "com.gu.notifications.worker.AndroidSender::handleRequest")

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

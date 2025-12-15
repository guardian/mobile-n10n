import { GuStack } from '@guardian/cdk/lib/constructs/core';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import {
	type App,
	CfnOutput,
	CfnParameter,
	Duration,
	Fn,
	Tags,
} from 'aws-cdk-lib';
import {
	Alarm,
	ComparisonOperator,
	Metric,
	TreatMissingData,
} from 'aws-cdk-lib/aws-cloudwatch';
import { SnsAction } from 'aws-cdk-lib/aws-cloudwatch-actions';
import type { IRepository } from 'aws-cdk-lib/aws-ecr';
import { Repository } from 'aws-cdk-lib/aws-ecr';
import {
	PolicyDocument,
	PolicyStatement,
	Role,
	ServicePrincipal,
} from 'aws-cdk-lib/aws-iam';
import {
	DockerImageCode,
	DockerImageFunction,
	EventSourceMapping,
} from 'aws-cdk-lib/aws-lambda';
import { Topic } from 'aws-cdk-lib/aws-sns';
import type { ITopic } from 'aws-cdk-lib/aws-sns';
import { Queue } from 'aws-cdk-lib/aws-sqs';
import {
	ParameterDataType,
	ParameterTier,
	StringParameter,
} from 'aws-cdk-lib/aws-ssm';
import { Construct } from 'constructs';

interface SenderWorkerOpts {
	handler: string;
	imageRepo: IRepository;
	buildId: string;
	reservedConcurrency: number;
	alarmTopic: ITopic;
	tooFewInvocationsAlarmPeriod: Duration;
	tooFewInvocationsEnabled: boolean;
	cleanerQueueArn: string;
	platform: string;
	paramPrefix: string;
	isBatchingSqsMessages: boolean;
	dailyAlarmPeriod: boolean;
	tooFewNotificationByTypeAlarms: boolean;
}

class SenderWorker extends Construct {
	readonly senderSqs: Queue;

	constructor(scope: GuStack, id: string, props: SenderWorkerOpts) {
		super(scope, id);

		Tags.of(this).add('App', id);

		const snsTopicAction = new SnsAction(props.alarmTopic);

		const senderDlq = new Queue(this, 'SenderDlq');
		this.senderSqs = new Queue(this, 'SenderSqs', {
			visibilityTimeout: props.isBatchingSqsMessages
				? Duration.seconds(190)
				: Duration.seconds(100),
			retentionPeriod: Duration.hours(1),
			deadLetterQueue: {
				queue: senderDlq,
				maxReceiveCount: 5,
			},
		});

		const executionRole = new Role(this, 'ExecutionRole', {
			assumedBy: new ServicePrincipal('lambda.amazonaws.com'),
			path: '/',
			inlinePolicies: {
				logs: new PolicyDocument({
					statements: [
						new PolicyStatement({
							actions: ['logs:CreateLogGroup'],
							resources: [`arn:aws:logs:eu-west-1:${scope.account}:*`],
						}),
						new PolicyStatement({
							actions: ['logs:CreateLogStream', 'logs:PutLogEvents'],
							resources: [
								`arn:aws:logs:eu-west-1:${scope.account}:log-group:/aws/lambda/*:*`,
							],
						}),
					],
				}),
				SQSOutput: new PolicyDocument({
					statements: [
						new PolicyStatement({
							actions: ['sqs:*'],
							resources: [this.senderSqs.queueArn],
						}),
					],
				}),
				SQSInput: new PolicyDocument({
					statements: [
						new PolicyStatement({
							actions: ['sqs:SendMessage'],
							resources: [props.cleanerQueueArn],
						}),
					],
				}),
				Conf: new PolicyDocument({
					statements: [
						new PolicyStatement({
							actions: ['ssm:GetParametersByPath'],
							resources: [
								`arn:aws:ssm:${scope.region}:${scope.account}:parameter/notifications/${scope.stage}/workers/${props.platform}`,
							],
						}),
					],
				}),
				Cloudwatch: new PolicyDocument({
					statements: [
						new PolicyStatement({
							actions: ['cloudwatch:PutMetricData'],
							resources: ['*'],
						}),
					],
				}),
			},
		});

		const codeImage = DockerImageCode.fromEcr(props.imageRepo, {
			cmd: [props.handler],
			tag: props.buildId,
		});

		const senderLambdaCtr = new DockerImageFunction(this, 'SenderLambdaCtr', {
			functionName: `${scope.stack}-${id}-sender-ctr-${scope.stage}`,
			code: codeImage,
			environment: {
				Stage: scope.stage,
				Stack: scope.stack,
				App: id,
				Platform: props.platform,
			},
			memorySize: 10240,
			description: `sends notifications for ${id}`,
			role: executionRole,
			timeout: props.isBatchingSqsMessages
				? Duration.seconds(180)
				: Duration.seconds(90),
			reservedConcurrentExecutions: props.reservedConcurrency,
		});

		const senderSqsEventSourceMapping = new EventSourceMapping(
			this,
			'SenderSqsEventSourceMapping',
			{
				batchSize: props.isBatchingSqsMessages ? 20 : 1,
				maxBatchingWindow: props.isBatchingSqsMessages
					? Duration.seconds(1)
					: Duration.seconds(0),
				enabled: true,
				eventSourceArn: this.senderSqs.queueArn,
				target: senderLambdaCtr,
			},
		);
		senderSqsEventSourceMapping.node.addDependency(this.senderSqs);
		senderSqsEventSourceMapping.node.addDependency(senderLambdaCtr);

		const senderThrottleAlarm = new Alarm(this, 'SenderThrottleAlarm', {
			alarmDescription: `Triggers if the ${id} sender lambda is throttled in ${scope.stage}.`,
			comparisonOperator: ComparisonOperator.GREATER_THAN_THRESHOLD,
			evaluationPeriods: 1,
			threshold: 0,
			metric: senderLambdaCtr.metricThrottles({
				period: Duration.seconds(360),
				statistic: 'Sum',
			}),
			treatMissingData: TreatMissingData.NOT_BREACHING,
		});
		senderThrottleAlarm.addAlarmAction(snsTopicAction);
		senderThrottleAlarm.addOkAction(snsTopicAction);

		const senderErrorAlarm = new Alarm(this, 'SenderErrorAlarm', {
			alarmDescription: `Triggers if the ${id} sender lambda errors in ${scope.stage}.`,
			comparisonOperator: ComparisonOperator.GREATER_THAN_THRESHOLD,
			evaluationPeriods: 1,
			threshold: 0,
			metric: senderLambdaCtr.metricErrors({
				period: Duration.seconds(360),
				statistic: 'Sum',
			}),
			treatMissingData: TreatMissingData.NOT_BREACHING,
		});
		senderErrorAlarm.addAlarmAction(snsTopicAction);
		senderErrorAlarm.addOkAction(snsTopicAction);

		const senderTooFewInvocationsAlarm = new Alarm(
			this,
			'SenderTooFewInvocationsAlarm',
			{
				alarmDescription: `Triggers if the ${id} sender lambda is not frequently invoked in ${scope.stage}.`,
				comparisonOperator: ComparisonOperator.LESS_THAN_OR_EQUAL_TO_THRESHOLD,
				evaluationPeriods: 1,
				threshold: 0,
				// whole day for editions, 60 minutes for others
				metric: senderLambdaCtr.metricInvocations({
					period: Duration.seconds(
						props.dailyAlarmPeriod ? 60 * 60 * 24 : 60 * 60,
					),
					statistic: 'Sum',
				}),
				treatMissingData: TreatMissingData.BREACHING,
				actionsEnabled: true, // isEnabled
			},
		);
		senderTooFewInvocationsAlarm.addAlarmAction(snsTopicAction);
		senderTooFewInvocationsAlarm.addOkAction(snsTopicAction);

		if (props.tooFewNotificationByTypeAlarms) {
			const nonBreakingCountMetric = new Metric({
				namespace: `Notifications/${scope.stage}/workers`,
				metricName: 'worker.notificationProcessingTime',
				period: Duration.minutes(15),
				statistic: 'SampleCount',
				dimensionsMap: {
					platform: id,
					type: 'other',
				},
			});
			const senderTooFewNonBreakingAlarm = new Alarm(
				this,
				'SenderTooFewNonBreakingAlarm',
				{
					alarmDescription: `Triggers if the ${id} sender lambda is not frequently invoked for non-breaking news notification in ${scope.stage}.`,
					comparisonOperator:
						ComparisonOperator.LESS_THAN_OR_EQUAL_TO_THRESHOLD,
					evaluationPeriods: 8, // 2 hours
					threshold: 0,
					metric: nonBreakingCountMetric,
					treatMissingData: TreatMissingData.BREACHING,
					actionsEnabled: scope.stage === 'PROD',
				},
			);
			senderTooFewNonBreakingAlarm.addAlarmAction(snsTopicAction);
			senderTooFewNonBreakingAlarm.addOkAction(snsTopicAction);

			const breakingNewsCountMetric = new Metric({
				namespace: `Notifications/${scope.stage}/workers`,
				metricName: 'worker.notificationProcessingTime',
				period: Duration.minutes(15),
				statistic: 'SampleCount',
				dimensionsMap: {
					platform: id,
					type: 'breakingNews',
				},
			});
			const senderTooFewBreakingNewsAlarm = new Alarm(
				this,
				'SenderTooFewBreakingNewsAlarm',
				{
					alarmDescription: `Triggers if the ${id} sender lambda is not frequently invoked for breaking news notification in ${scope.stage}.`,
					comparisonOperator:
						ComparisonOperator.LESS_THAN_OR_EQUAL_TO_THRESHOLD,
					evaluationPeriods: 48, // 12 hours
					threshold: 0,
					metric: breakingNewsCountMetric,
					treatMissingData: TreatMissingData.BREACHING,
					actionsEnabled: scope.stage === 'PROD',
				},
			);
			senderTooFewBreakingNewsAlarm.addAlarmAction(snsTopicAction);
			senderTooFewBreakingNewsAlarm.addOkAction(snsTopicAction);
		}

		// this advertises the name of the sender queue to the harvester app
		new StringParameter(this, 'SenderQueueSSMParameter', {
			parameterName: `/notifications/${scope.stage}/workers/harvester/${props.paramPrefix}SqsCdkUrl`,
			simpleName: false,
			stringValue: this.senderSqs.queueUrl,
			tier: ParameterTier.STANDARD,
			dataType: ParameterDataType.TEXT,
		});
	}
}

export class SenderWorkerStack extends GuStack {
	constructor(scope: App, id: string, props: GuStackProps) {
		super(scope, id, props);

		const senderTooFewInvocationsAlarmPeriodParam = new CfnParameter(
			this,
			'SenderTooFewInvocationsAlarmPeriodParam',
			{
				type: 'Number',
				description: 'How long until no execution is suspicious, in seconds',
			},
		);

		const reservedConcurrencyParam = new CfnParameter(
			this,
			'ReservedConcurrency',
			{
				type: 'Number',
				description:
					'How many concurrent execution to provision the lamdba with',
			},
		);

		const buildIdParam = new CfnParameter(this, 'BuildId', {
			type: 'String',
			description:
				'build id from teamcity, the image should be tagged with this',
		});

		const alarmTopicArnParam = new CfnParameter(this, 'AlarmTopicArn', {
			type: 'String',
			description:
				'The ARN of the SNS topic to send all the cloudwatch alarms to',
		});

		const cleanerQueueArnParam = new CfnParameter(
			this,
			'CleanerQueueArnParam',
			{
				type: 'String',
				description: 'The ARN of the cleaner SQS queue',
			},
		);

		const notificationEcrRepo = Repository.fromRepositoryAttributes(
			this,
			'NotificationLambdaRepository',
			{
				repositoryArn: Fn.importValue('NotificationLambdaRepositoryArn'),
				repositoryName: Fn.importValue('NotificationLambdaRepositoryName'),
			},
		);

		const sharedOpts = {
			imageRepo: notificationEcrRepo,
			buildId: buildIdParam.valueAsString,
			reservedConcurrency: reservedConcurrencyParam.valueAsNumber,
			alarmTopic: Topic.fromTopicArn(
				this,
				'AlarmTopic',
				alarmTopicArnParam.valueAsString,
			),
			tooFewInvocationsAlarmPeriod: Duration.seconds(
				senderTooFewInvocationsAlarmPeriodParam.valueAsNumber,
			),
			tooFewInvocationsEnabled: props.stage === 'PROD',
			cleanerQueueArn: cleanerQueueArnParam.valueAsString,
		};

		const workerQueueArns: string[] = [];

		const addWorker = (
			workerName: string,
			paramPrefix: string,
			handler: string,
			isBatchingSqsMessages: boolean = false,
			dailyAlarmPeriod: boolean = false,
			tooFewNotificationByTypeAlarms: boolean = false,
		) => {
			const worker = new SenderWorker(this, workerName, {
				...props,
				platform: workerName,
				paramPrefix: paramPrefix,
				handler: handler,
				isBatchingSqsMessages,
				...sharedOpts,
				dailyAlarmPeriod: dailyAlarmPeriod,
				tooFewNotificationByTypeAlarms: tooFewNotificationByTypeAlarms,
			});
			workerQueueArns.push(worker.senderSqs.queueArn);
		};

		/*
		 * add each of the worker lambdas, where each one handles a different
		 * platform or app by talking to a different lambda handler function
		 */

		addWorker(
			'ios',
			'iosLive',
			'com.gu.notifications.worker.IOSSender::handleChunkTokens',
			false,
			false,
			true,
		);
		addWorker(
			'android',
			'androidLive',
			'com.gu.notifications.worker.AndroidSender::handleChunkTokens',
			true,
			false,
			true,
		);
		// edition apps only send one notification a day in order to get content for that day
		addWorker(
			'ios-edition',
			'iosEdition',
			'com.gu.notifications.worker.IOSSender::handleChunkTokens',
			false,
			true,
		);
		addWorker(
			'android-edition',
			'androidEdition',
			'com.gu.notifications.worker.AndroidSender::handleChunkTokens',
			false,
			true,
		);

		addWorker(
			'android-beta',
			'androidBeta',
			'com.gu.notifications.worker.AndroidSender::handleChunkTokens',
		);

		/*
		 * each worker has been assigned an SQS queue which, when written to, will
		 * trigger it to send its notifications. Here, we export the list of worker
		 * queue ARNs so that it can be used in other stacks, for example, Harvester
		 * needs to give itself permission to write to these queues.
		 */
		new CfnOutput(this, 'NotificationSenderWorkerQueueArns', {
			exportName: 'NotificationSenderWorkerQueueArns-' + this.stage,
			value: Fn.join(',', workerQueueArns),
		});
	}
}

import { GuAlarm } from '@guardian/cdk/lib/constructs/cloudwatch';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuStack } from '@guardian/cdk/lib/constructs/core';
import { GuLambdaFunction } from '@guardian/cdk/lib/constructs/lambda';
import type { App } from 'aws-cdk-lib';
import { Duration, Tags } from 'aws-cdk-lib';
import {
	ComparisonOperator,
	Metric,
	TreatMissingData,
} from 'aws-cdk-lib/aws-cloudwatch';
import { AttributeType, BillingMode, Table } from 'aws-cdk-lib/aws-dynamodb';
import { EventBus, Rule, Schedule } from 'aws-cdk-lib/aws-events';
import { LambdaFunction } from 'aws-cdk-lib/aws-events-targets';
import { Effect, PolicyStatement } from 'aws-cdk-lib/aws-iam';
import { Runtime } from 'aws-cdk-lib/aws-lambda';
import { SqsDestination } from 'aws-cdk-lib/aws-lambda-destinations';
import { Queue } from 'aws-cdk-lib/aws-sqs';

const addDlqAlarm = (scope: GuStack, app: string, dlq: Queue, name: string) => {
	new GuAlarm(scope, `${name}Alarm`, {
		app: app,
		alarmName: `${name}-alarm`,
		alarmDescription: `More than 1 message in ${name}`,
		metric: new Metric({
			namespace: 'AWS/SQS',
			metricName: 'ApproximateNumberOfMessagesVisible',
			dimensionsMap: { QueueName: dlq.queueName },
			statistic: 'Sum',
			period: Duration.minutes(1),
		}),
		threshold: 1,
		evaluationPeriods: 1,
		comparisonOperator: ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD,
		treatMissingData: TreatMissingData.NOT_BREACHING,
		snsTopicName: 'mobile-server-side',
	});
};

export class LiveActivities extends GuStack {
	constructor(scope: App, id: string, props: GuStackProps) {
		super(scope, id, props);

		const { stack, stage, region, account } = this;
		const app = 'liveactivities';

		const dynamoTableName = `${stack}-liveactivities-${stage}`;

		const dynamoTable = new Table(this, `${app}-dynamo-table`, {
			tableName: dynamoTableName,
			partitionKey: { name: 'id', type: AttributeType.STRING },
			billingMode: BillingMode.PAY_PER_REQUEST,
			timeToLiveAttribute: 'ttlInEpochSeconds',
		});

		Tags.of(dynamoTable).add('devx-backup-enabled', 'true');

		const channelManagerDlq = new Queue(this, 'ChannelManagerDlq', {
			queueName: `${app}-channel-manager-dlq-${stage}`,
			visibilityTimeout: Duration.minutes(4),
			retentionPeriod: Duration.days(14),
		});
		addDlqAlarm(
			this,
			app,
			channelManagerDlq,
			`${app}-channel-manager-dlq-${stage}`,
		);

		const channelLambda = new GuLambdaFunction(
			this,
			`${app}-channel-manager-lambda`,
			{
				app: app,
				description: 'Manages channels for live activities',
				handler: 'com.gu.liveactivities.ChannelManagerLambda::handleRequest',
				functionName: `${app}-channel-manager-${stage}`,
				fileName: `${app}.jar`,
				runtime: Runtime.JAVA_11,
				memorySize: 1024,
				timeout: Duration.minutes(3),
				onFailure: new SqsDestination(channelManagerDlq),
				retryAttempts: 2,
				environment: {
					Stack: stack,
					Stage: stage,
					App: app,
					DYNAMODB_TABLE_NAME: dynamoTableName,
				},
			},
		);

		channelLambda.addToRolePolicy(
			new PolicyStatement({
				actions: [
					'dynamodb:GetItem',
					'dynamodb:PutItem',
					'dynamodb:UpdateItem',
					'dynamodb:Query',
				],
				effect: Effect.ALLOW,
				resources: [dynamoTable.tableArn],
			}),
		);
		channelLambda.addToRolePolicy(
			new PolicyStatement({
				actions: ['ssm:GetParametersByPath'],
				effect: Effect.ALLOW,
				resources: [
					`arn:aws:ssm:${region}:${account}:parameter/notifications/${stage}/liveactivities/ios`,
				],
			}),
		);

		channelLambda.addToRolePolicy(
			new PolicyStatement({
				actions: ['events:PutEvents'],
				resources: [
					`arn:aws:events:${region}:${account}:event-bus/${app}-eventbus-${stage}`,
				],
			}),
		);

		const broadcastDlq = new Queue(this, 'BroadcastDlq', {
			queueName: `${app}-broadcast-dlq-${stage}`,
			visibilityTimeout: Duration.minutes(4),
			retentionPeriod: Duration.days(14),
		});
		addDlqAlarm(this, app, broadcastDlq, `${app}-broadcast-dlq-${stage}`);

		const broadcastLambda = new GuLambdaFunction(
			this,
			`${app}-broadcast-lambda`,
			{
				app: app,
				description: 'Broadcasts messages for live activities',
				handler: 'com.gu.liveactivities.BroadcastLambda::handleRequest',
				functionName: `${app}-broadcast-${stage}`,
				fileName: `${app}.jar`,
				runtime: Runtime.JAVA_11,
				memorySize: 1024,
				timeout: Duration.minutes(3),
				onFailure: new SqsDestination(broadcastDlq),
				retryAttempts: 2,
				environment: {
					Stack: stack,
					Stage: stage,
					App: app,
					DYNAMODB_TABLE_NAME: dynamoTableName,
				},
			},
		);

		broadcastLambda.addToRolePolicy(
			new PolicyStatement({
				actions: [
					'dynamodb:GetItem',
					'dynamodb:PutItem',
					'dynamodb:UpdateItem',
					'dynamodb:Query',
				],
				effect: Effect.ALLOW,
				resources: [dynamoTable.tableArn],
			}),
		);
		broadcastLambda.addToRolePolicy(
			new PolicyStatement({
				actions: ['ssm:GetParametersByPath'],
				effect: Effect.ALLOW,
				resources: [
					`arn:aws:ssm:${region}:${account}:parameter/notifications/${stage}/liveactivities/ios`,
				],
			}),
		);

		//////////// Channel Clean up lambda //////////////

		const channelCleanerDlq = new Queue(this, 'ChannelCleanerDlq', {
			queueName: `${app}-channel-cleaner-dlq-${stage}`,
			visibilityTimeout: Duration.minutes(4),
			retentionPeriod: Duration.days(14),
		});
		addDlqAlarm(
			this,
			app,
			channelCleanerDlq,
			`${app}-channel-cleaner-dlq-${stage}`,
		);

		const channelCleanerLambda = new GuLambdaFunction(
			this,
			`${app}-channel-cleaner-lambda`,
			{
				app: app,
				description: 'Clean up channels for live activities',
				handler: 'com.gu.liveactivities.ChannelCleanUpLambda::handleRequest',
				functionName: `${app}-channel-cleaner-${stage}`,
				fileName: `${app}.jar`,
				runtime: Runtime.JAVA_11,
				memorySize: 1024,
				timeout: Duration.minutes(11),
				onFailure: new SqsDestination(channelCleanerDlq),
				retryAttempts: 2,
				environment: {
					Stack: stack,
					Stage: stage,
					App: app,
					DYNAMODB_TABLE_NAME: dynamoTableName,
				},
			},
		);

		// Note: scheduled rules must use the default event bus, not custom eventBus
		new Rule(this, 'ChannelCleanerSchedule', {
			schedule: Schedule.cron({ hour: '5', minute: '0' }), // 5am UTC
			targets: [new LambdaFunction(channelCleanerLambda)],
		});

		channelCleanerLambda.addToRolePolicy(
			new PolicyStatement({
				actions: [
					'dynamodb:GetItem',
					'dynamodb:UpdateItem',
					'dynamodb:Query',
					'dynamodb:Scan',
				],
				effect: Effect.ALLOW,
				resources: [dynamoTable.tableArn],
			}),
		);

		channelCleanerLambda.addToRolePolicy(
			new PolicyStatement({
				actions: ['ssm:GetParametersByPath'],
				effect: Effect.ALLOW,
				resources: [
					`arn:aws:ssm:${region}:${account}:parameter/notifications/${stage}/liveactivities/ios`,
				],
			}),
		);

		//////////// EVENTBUS INFRASTRUCTURE //////////////

		const eventBus = new EventBus(this, 'Events', {
			eventBusName: `${app}-eventbus-${stage}`,
			description: `${stage} event routing for live activities`,
		});

		new Rule(this, 'ChannelRule', {
			eventBus: eventBus,
			eventPattern: {
				source: ['football-lambda'],
				detailType: ['channel-create', 'channel-delete'],
			},
			targets: [new LambdaFunction(channelLambda)],
			description:
				'Route channel create/delete events from football to channel manager lambda',
		});

		new Rule(this, 'BroadcastRule', {
			eventBus: eventBus,
			eventPattern: {
				source: ['football-lambda'],
				detailType: ['broadcast-update', 'broadcast-end'],
			},
			targets: [new LambdaFunction(broadcastLambda)],
			description:
				'Route broadcast update/end events from football to broadcast lambda',
		});

		new Rule(this, 'InitialBroadcastRule', {
			eventBus: eventBus,
			eventPattern: {
				source: ['channel-manager-lambda'],
				detailType: ['broadcast-update'],
			},
			targets: [new LambdaFunction(broadcastLambda)],
			description:
				'Route initial broadcast update event from channel manager to broadcast lambda',
		});
	}
}

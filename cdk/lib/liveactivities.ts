import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuStack } from '@guardian/cdk/lib/constructs/core';
import { GuLambdaFunction } from '@guardian/cdk/lib/constructs/lambda';
import type { App } from 'aws-cdk-lib';
import { Duration, Tags } from 'aws-cdk-lib';
import { AttributeType, BillingMode, Table } from 'aws-cdk-lib/aws-dynamodb';
import { EventBus, Rule } from 'aws-cdk-lib/aws-events';
import { SqsQueue } from 'aws-cdk-lib/aws-events-targets';
import { Effect, PolicyStatement } from 'aws-cdk-lib/aws-iam';
import { Runtime } from 'aws-cdk-lib/aws-lambda';
import { Queue } from 'aws-cdk-lib/aws-sqs';

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
		});
		Tags.of(dynamoTable).add('devx-backup-enabled', 'true');

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
				timeout: Duration.seconds(120),
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
				actions: ['dynamodb:PutItem', 'dynamodb:UpdateItem', 'dynamodb:Query'],
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
				timeout: Duration.seconds(120),
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
				actions: ['dynamodb:PutItem', 'dynamodb:UpdateItem', 'dynamodb:Query'],
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

		//////////// EVENTBUS INFRASTRUCTURE //////////////

		const eventBus = new EventBus(this, 'Events', {
			eventBusName: `${app}-eventbus-${stage}`,
			description: `${stage} event routing for live activities`,
		});

		// Development SQS to capture and inspect events from PA polling during development
		const liveGameTestingQueue = new Queue(
			this,
			`${app}-football-live-games-${stage}`,
			{
				queueName: `${app}-football-live-games-${stage}`,
				retentionPeriod: Duration.days(7),
			},
		);

		new Rule(this, 'liveGameEventsTargeting', {
			eventBus: eventBus,
			description: `Deliver live match events in ${stage} to liveGameTestingQueue`,
			eventPattern: {
				source: ['football-lambda'],
				detailType: ['football-match-events-with-articleId'],
			},
			enabled: false, // only enable if we want to inspect events in the development queue
			targets: [new SqsQueue(liveGameTestingQueue)],
		});
	}
}

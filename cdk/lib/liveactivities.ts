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

		const { stack, stage } = this;
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
					`arn:aws:ssm:${this.region}:${this.account}:parameter/notifications/${this.stage}/liveactivities/ios`,
				],
			}),
		);

		//////////// PA POLLING INFRASTRUCTURE //////////////
		const liveGamesPaPollingLambda = new GuLambdaFunction(
			this,
			`${app}-live-games-pa-polling-lambda`,
			{
				app: app,
				description: 'Polls PA for live game updates and routes to event bus',
				handler:
					'com.gu.liveactivities.PollingLiveGamesDataLambda::handleRequest',
				functionName: `${app}-live-games-pa-polling-${stage}`,
				fileName: `${app}.jar`,
				runtime: Runtime.JAVA_11,
				memorySize: 1024,
				timeout: Duration.seconds(120),
				environment: {
					Stack: stack,
					Stage: stage,
					App: app,
				},
			},
		);

		const eventBus = new EventBus(this, 'Events', {
			eventBusName: `${app}-eventbus-${scope.stage}`,
			description: `${scope.stage} event routing for live activities`,
		});

		liveGamesPaPollingLambda.addToRolePolicy(
			new PolicyStatement({
				actions: ['events:PutEvents'],
				resources: [eventBus.eventBusArn],
			}),
		);

		// Development SQS to capture and inspect events from PA polling during development
		const liveGameTestingQueue = new Queue(
			this,
			`${app}-football-live-games-${scope.stage}`,
			{
				queueName: `${app}-football-live-games-${scope.stage}`,
				retentionPeriod: Duration.days(7),
			},
		);

		new Rule(this, 'liveGameEventsTargeting', {
			eventBus: eventBus,
			description: `Deliver live game events from PA polling lambda ${scope.stage} to liveGameTestingQueue`,
			eventPattern: {
				source: ['pa-live-game-updates'],
			},
			enabled: false, // only enable if we want to inspect events in the development queue
			targets: [new SqsQueue(liveGameTestingQueue)],
		});
	}
}

import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuStack } from '@guardian/cdk/lib/constructs/core';
import { GuLambdaFunction } from '@guardian/cdk/lib/constructs/lambda';
import type { App } from 'aws-cdk-lib';
import { Duration, Tags } from 'aws-cdk-lib';
import { AttributeType, BillingMode, Table } from 'aws-cdk-lib/aws-dynamodb';
import { Effect, PolicyStatement } from 'aws-cdk-lib/aws-iam';
import { Runtime } from 'aws-cdk-lib/aws-lambda';

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
					`arn:aws:ssm:${this.region}:${this.account}:parameter/notifications/${this.stage}/liveactivities/ios`,
				],
			}),
		);
	}
}

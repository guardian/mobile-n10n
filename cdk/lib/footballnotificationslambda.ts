import { join } from 'path';
import { GuScheduledLambda } from '@guardian/cdk';
import { GuAlarm } from '@guardian/cdk/lib/constructs/cloudwatch';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuStack } from '@guardian/cdk/lib/constructs/core';
import type { App } from 'aws-cdk-lib';
import { Duration } from 'aws-cdk-lib';
import {
	ComparisonOperator,
	Metric,
	TreatMissingData,
	Unit,
} from 'aws-cdk-lib/aws-cloudwatch';
import { Schedule } from 'aws-cdk-lib/aws-events';
import { Effect, PolicyStatement } from 'aws-cdk-lib/aws-iam';
import { LoggingFormat, Runtime } from 'aws-cdk-lib/aws-lambda';
import { LogGroup, MetricFilter } from 'aws-cdk-lib/aws-logs';
import { CfnInclude } from 'aws-cdk-lib/cloudformation-include';

export class FootballNotificationsLambda extends GuStack {
	constructor(scope: App, id: string, props: GuStackProps) {
		super(scope, id, props);

		const { stack, stage, region, account } = this;
		const app = 'football';

		const yamlTemplateFilePath = join(__dirname, '../../football/cfn.yaml');
		new CfnInclude(this, 'YamlTemplate', {
			templateFile: yamlTemplateFilePath,
		});

		const footballnotificationslambda = new GuScheduledLambda(
			this,
			`${app}-Lambda`,
			{
				app: app,
				description: 'Send Goal Alert notifications',
				handler: 'com.gu.mobile.notifications.football.Lambda::handler',
				functionName: [app, stage].join('-'),
				fileName: `${app}.jar`,
				monitoringConfiguration: { noMonitoring: true },
				loggingFormat: LoggingFormat.TEXT,
				//MinuteEvent
				rules: [
					{
						// Run every minute.
						schedule: Schedule.expression('cron(* * * * ? *)'),
					},
				],
				runtime: Runtime.JAVA_11,
				memorySize: 1024,
				timeout: Duration.seconds(60),
				environment: {
					Stack: stack,
					Stage: stage,
					App: app,
				},
			},
		);

		footballnotificationslambda.addToRolePolicy(
			new PolicyStatement({
				actions: ['ssm:GetParametersByPath'],
				effect: Effect.ALLOW,
				resources: [
					`arn:aws:ssm:${region}:${account}:parameter/${app}/${stage}/${stack}`,
				],
			}),
		);

		// Keep dynoma table defition in the yaml for now
		const dynamoTableName = `${stack}-football-notifications-${stage}`;

		footballnotificationslambda.addToRolePolicy(
			new PolicyStatement({
				actions: ['dynamodb:PutItem', 'dynamodb:UpdateItem', 'dynamodb:Query'],
				effect: Effect.ALLOW,
				resources: [
					`arn:aws:dynamodb:${region}:${account}:table/${dynamoTableName}`,
				],
			}),
		);

		// const dynamoTable = new Table(this, 'DynamoTable', {
		// 	tableName: dynamoTableName,
		// 	partitionKey: { name: 'notificationId', type: AttributeType.STRING },
		// 	billingMode: BillingMode.PROVISIONED,
		// 	readCapacity: 3,
		// 	writeCapacity: 3,
		// 	timeToLiveAttribute: 'ttl',
		// 	removalPolicy: RemovalPolicy.RETAIN,
		// });
		// Tags.of(dynamoTable).add('devx-backup-enabled', 'true');
		//
		// footballnotificationslambda.addToRolePolicy(
		// 	new PolicyStatement({
		// 		actions: ['dynamodb:PutItem', 'dynamodb:UpdateItem', 'dynamodb:Query'],
		// 		effect: Effect.ALLOW,
		// 		resources: [
		// 			`arn:aws:dynamodb:${region}:${account}:table/${dynamoTableName}`,
		// 		],
		// 	}),
		// );

		// Read Throttle Events Alarm
		new GuAlarm(this, 'MobileNotificationsFootballConsumedReadThrottleEvents', {
			app,
			alarmName: 'MobileNotificationsFootballConsumedReadThrottleEvents',
			alarmDescription:
				'Triggers if DynamoDB ReadThrottleEvents >= 10 in 5 minutes',
			snsTopicName: 'dynamodb',
			metric: new Metric({
				namespace: 'AWS/DynamoDB',
				metricName: 'ReadThrottleEvents',
				dimensionsMap: { TableName: dynamoTableName },
				period: Duration.minutes(5),
				statistic: 'sum',
				unit: Unit.COUNT,
			}),
			threshold: 10,
			evaluationPeriods: 1,
			comparisonOperator: ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD,
			treatMissingData: TreatMissingData.NOT_BREACHING,
		});

		// Write Throttle Events Alarm
		new GuAlarm(
			this,
			'MobileNotificationsFootballConsumedWriteThrottleEvents',
			{
				app,
				alarmName: 'MobileNotificationsFootballConsumedWriteThrottleEvents',
				alarmDescription:
					'Triggers if DynamoDB WriteThrottleEvents >= 10 in 5 minutes',
				snsTopicName: 'dynamodb',
				metric: new Metric({
					namespace: 'AWS/DynamoDB',
					metricName: 'WriteThrottleEvents',
					dimensionsMap: { TableName: dynamoTableName },
					period: Duration.minutes(5),
					statistic: 'sum',
					unit: Unit.COUNT,
				}),
				threshold: 10,
				evaluationPeriods: 1,
				comparisonOperator:
					ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD,
				treatMissingData: TreatMissingData.NOT_BREACHING,
			},
		);

		const footballNotificationLambdaLogGroupName = `/aws/lambda/${footballnotificationslambda.functionName}`;
		const footballNotificationLambdaLogGroup = LogGroup.fromLogGroupName(
			this,
			'FootballLambdaLogGroup',
			footballNotificationLambdaLogGroupName,
		);

		// GoalEvent MetricFilter
		new MetricFilter(this, 'GoalEventMetricFilter', {
			logGroup: footballNotificationLambdaLogGroup,
			filterPattern: { logPatternString: 'successfully sent' },
			metricNamespace: `${stage}/football-notifications`,
			metricName: 'goal-success',
			metricValue: '1',
		});

		// ErrorEvent MetricFilter
		new MetricFilter(this, 'ErrorEventMetricFilter', {
			logGroup: footballNotificationLambdaLogGroup,
			filterPattern: { logPatternString: 'Error' },
			metricNamespace: `${stage}/football-notifications`,
			metricName: 'error',
			metricValue: '1',
		});
	}
}

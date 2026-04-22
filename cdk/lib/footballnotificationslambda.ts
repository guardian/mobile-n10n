import { GuScheduledLambda } from '@guardian/cdk';
import { GuAlarm } from '@guardian/cdk/lib/constructs/cloudwatch';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuStack } from '@guardian/cdk/lib/constructs/core';
import type { App } from 'aws-cdk-lib';
import { Duration, Tags } from 'aws-cdk-lib';
import {
	ComparisonOperator,
	Metric,
	TreatMissingData,
	Unit,
} from 'aws-cdk-lib/aws-cloudwatch';
import { AttributeType, BillingMode, Table } from 'aws-cdk-lib/aws-dynamodb';
import { Schedule } from 'aws-cdk-lib/aws-events';
import { Effect, PolicyStatement } from 'aws-cdk-lib/aws-iam';
import { LoggingFormat, Runtime } from 'aws-cdk-lib/aws-lambda';
import { LogGroup, MetricFilter } from 'aws-cdk-lib/aws-logs';

export class FootballNotificationsLambda extends GuStack {
	constructor(scope: App, id: string, props: GuStackProps) {
		super(scope, id, props);

		const { stack, stage, region, account } = this;
		const app = 'football';

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

		footballnotificationslambda.addToRolePolicy(
			new PolicyStatement({
				actions: ['events:PutEvents'],
				resources: [
					`arn:aws:events:${region}:${account}:event-bus/liveactivities-eventbus-${stage}`,
				],
			}),
		);

		const dynamoTableName = `${stack}-football-notifications-${stage}`;

		const dynamoTable = new Table(this, 'DynamoTable', {
			tableName: dynamoTableName,
			partitionKey: { name: 'notificationId', type: AttributeType.STRING },
			billingMode: BillingMode.PROVISIONED,
			readCapacity: 3,
			writeCapacity: 3,
			timeToLiveAttribute: 'ttl',
		});
		this.overrideLogicalId(dynamoTable, {
			logicalId: 'DynamoTable',
			reason: 'Retaining a stateful resource previously defined in YAML',
		});

		Tags.of(dynamoTable).add('devx-backup-enabled', 'true');

    // Live Activities DynamoDB Table
    const liveActivitiesAppName = 'liveactivities';
    const liveActivitiesDynamoTableName = `${stack}-${liveActivitiesAppName}-payload-${stage}`;

    const liveActivitiesDynamoTable = new Table(
      this,
      'LiveActivitiesDynamoTable',
      {
        tableName: liveActivitiesDynamoTableName,
        partitionKey: { name: 'id', type: AttributeType.STRING },
        billingMode: BillingMode.PAY_PER_REQUEST,
        timeToLiveAttribute: 'ttl',
      },
    );
    Tags.of(liveActivitiesDynamoTable).add('devx-backup-enabled', 'true');

		footballnotificationslambda.addToRolePolicy(
			new PolicyStatement({
				actions: ['dynamodb:PutItem', 'dynamodb:UpdateItem', 'dynamodb:Query'],
				effect: Effect.ALLOW,
				resources: [
					`arn:aws:dynamodb:${region}:${account}:table/${dynamoTableName}`,
          `arn:aws:dynamodb:${region}:${account}:table/${liveActivitiesDynamoTableName}`,
				],
			}),
		);

		footballnotificationslambda.addToRolePolicy(
			new PolicyStatement({
				actions: ['dynamodb:PutItem', 'dynamodb:UpdateItem', 'dynamodb:Query'],
				effect: Effect.ALLOW,
				resources: [
					`arn:aws:dynamodb:${region}:${account}:table/${dynamoTableName}`,
				],
			}),
		);

		// Read Throttle Events Alarm
		new GuAlarm(this, 'MobileNotificationsFootballConsumedReadThrottleEvents', {
			app,
			alarmName: `MobileNotificationsFootballConsumedReadThrottleEvents-${stage}`,
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
				alarmName: `MobileNotificationsFootballConsumedWriteThrottleEvents-${stage}`,
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

    // Read Live Activities Throttle Events Alarm
    new GuAlarm(
      this,
      'MobileLiveActivitiesFootballConsumedReadThrottleEvents',
      {
        app,
        alarmName: `MobileLiveActivitiesFootballConsumedReadThrottleEvents-${stage}`,
        alarmDescription:
          'Triggers if DynamoDB ReadThrottleEvents >= 10 in 5 minutes',
        snsTopicName: 'dynamodb',
        metric: new Metric({
          namespace: 'AWS/DynamoDB',
          metricName: 'ReadThrottleEvents',
          dimensionsMap: { TableName: liveActivitiesDynamoTableName },
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

    // Write Live Activities Throttle Events Alarm
    new GuAlarm(
      this,
      'MobileLiveActivitiesFootballConsumedWriteThrottleEvents',
      {
        app,
        alarmName: `MobileLiveActivitiesFootballConsumedWriteThrottleEvents-${stage}`,
        alarmDescription:
          'Triggers if DynamoDB WriteThrottleEvents >= 10 in 5 minutes',
        snsTopicName: 'dynamodb',
        metric: new Metric({
          namespace: 'AWS/DynamoDB',
          metricName: 'WriteThrottleEvents',
          dimensionsMap: { TableName: liveActivitiesDynamoTableName },
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
      filterPattern: { logPatternString: 'Error sending match status' },
			metricNamespace: `${stage}/football-notifications`,
			metricName: 'error',
			metricValue: '1',
		});

    // Live Activities Event MetricFilter
    new MetricFilter(this, 'SuccessLiveActivitiesEventMetricFilter', {
      logGroup: footballNotificationLambdaLogGroup,
      filterPattern: {
        logPatternString: 'Successfully processed live activities event',
      },
      metricNamespace: `${stage}/${liveActivitiesAppName}-payload`,
      metricName: 'success',
      metricValue: '1',
    });

    // Live Activities ErrorEvent MetricFilter
    new MetricFilter(this, 'ErrorLiveActivitiesEventMetricFilter', {
      logGroup: footballNotificationLambdaLogGroup,
      filterPattern: {
        logPatternString: 'Failed to publish live activities event',
      },
      metricNamespace: `${stage}/${liveActivitiesAppName}-payload`,
      metricName: 'error',
      metricValue: '1',
    });
	}
}

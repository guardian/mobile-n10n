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
} from 'aws-cdk-lib/aws-cloudwatch';
import { Schedule } from 'aws-cdk-lib/aws-events';
import { Effect, PolicyStatement } from 'aws-cdk-lib/aws-iam';
import { LoggingFormat, Runtime } from 'aws-cdk-lib/aws-lambda';
import { adjustCloudformationParameters } from './mobile-n10n-compatibility';

export class FakeBreakingNewsLambda extends GuStack {
	constructor(scope: App, id: string, props: GuStackProps) {
		super(scope, id, props);

		const { stack, stage, region, account } = this;
		const app = 'fakebreakingnewslambda';

		const fakebreakingnewslambda = new GuScheduledLambda(
			this,
			`${app}-Lambda`,
			{
				app: app,
				description: 'Sends a fake breaking news',
				handler: `fakebreakingnews.FakeBreakingNewsLambda::handleRequest`,
				functionName: [stack, app, stage].join('-'),
				fileName: `${app}.jar`,
				monitoringConfiguration: { noMonitoring: true },
				loggingFormat: LoggingFormat.TEXT,
				rules: [
					{
						// Run at 5 minutes past every hour, every day.
						schedule: Schedule.expression('cron(5 * * * ? *)'),
					},
				],
				runtime: Runtime.JAVA_11,
				memorySize: 1024,
				timeout: Duration.seconds(300),
				environment: {
					Stack: stack,
					Stage: stage,
					App: app,
				},
			},
		);

		fakebreakingnewslambda.addToRolePolicy(
			new PolicyStatement({
				effect: Effect.ALLOW,
				actions: ['ssm:GetParametersByPath'],
				resources: [
					`arn:aws:ssm:${region}:${account}:parameter/notifications/${stage}/fakebreakingnews`,
				],
			}),
		);

		['android', 'ios'].forEach((platform) => {
			new GuAlarm(
				this,
				`NotEnough${platform.charAt(0).toUpperCase() + platform.slice(1)}FakeBreakingNewsAlarm`,
				{
					app: app,
					alarmName: `NotEnough${platform.charAt(0).toUpperCase() + platform.slice(1)}FakeBreakingNewsAlarm-${stage}`,
					alarmDescription: `Triggers if not enough ${platform.charAt(0).toUpperCase() + platform.slice(1)} dry run notifications are happening on ${stage}`,
					snsTopicName: `mobile-server-side`,
					metric: new Metric({
						namespace: `Notifications/${stage}/workers`,
						metricName: 'dryrun',
						dimensionsMap: { platform: platform },
						period: Duration.hours(2),
						statistic: 'sum',
					}),
					threshold: 1,
					evaluationPeriods: 1,
					comparisonOperator: ComparisonOperator.LESS_THAN_THRESHOLD,
					treatMissingData: TreatMissingData.BREACHING,
				},
			);
		});
		adjustCloudformationParameters(this);
	}
}

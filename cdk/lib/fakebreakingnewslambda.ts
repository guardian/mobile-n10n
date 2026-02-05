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
import { Runtime } from 'aws-cdk-lib/aws-lambda';

export class FakeBreakingNewsLambda extends GuStack {
	constructor(scope: App, id: string, props: GuStackProps) {
		super(scope, id, props);

		const { app, stack, stage } = this;

		// const yamlTemplateFilePath = join(
		// 	__dirname,
		// 	'../../fakebreakingnewslambda/fakebreakingnewslambda-cfn.yaml',
		// );
		// new CfnInclude(this, 'YamlTemplate', {
		// 	templateFile: yamlTemplateFilePath,
		// });

		const fakebreakingnewsApp = app ?? 'fakebreakingnews';

		new GuScheduledLambda(this, '${fakebreakingnewsApp}-Lambda', {
			app: fakebreakingnewsApp,
			description: 'Sends a fake breaking news',
			handler: `${fakebreakingnewsApp}.FakeBreakingNewsLambda::handleRequest`,
			functionName: [stack, fakebreakingnewsApp, stage].join('-'),
			fileName: `${fakebreakingnewsApp}.jar`,
			monitoringConfiguration: { noMonitoring: true },
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
				App: fakebreakingnewsApp,
			},
		});

		['android', 'ios'].forEach((platform) => {
			new GuAlarm(
				this,
				`FakeBreakingNewsAlarm for ${platform.charAt(0).toUpperCase() + platform.slice(1)}`,
				{
					app: fakebreakingnewsApp,
					alarmName: `NotEnough${platform.charAt(0).toUpperCase() + platform.slice(1)}FakeBreakingNewsAlarm`,
					alarmDescription: `Triggers if not enough ${platform.charAt(0).toUpperCase() + platform.slice(1)} dry run notifications are happening on ${stage}`,
					snsTopicName: `alarms-handler-topic-${this.stage}`,
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
	}
}

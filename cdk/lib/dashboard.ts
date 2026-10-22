import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuStack } from '@guardian/cdk/lib/constructs/core';
import type { App } from 'aws-cdk-lib';
import { Duration, Fn } from 'aws-cdk-lib';
import type { IMetric } from 'aws-cdk-lib/aws-cloudwatch';
import {
	Alarm,
	AlarmStatusWidget,
	Dashboard,
	GraphWidget,
	MathExpression,
	Metric,
	PeriodOverride,
	SingleValueWidget,
	TextWidget,
} from 'aws-cdk-lib/aws-cloudwatch';

export interface ServiceHealthDashboardProps extends GuStackProps {
	workerSqsQueueName: string;
	dbInstanceId: string;
}

const platforms = [
	'ios',
	'android',
	'ios-edition',
	'android-edition',
	'android-beta',
];
const liveActivityFunctions = [
	'broadcast',
	'channel-manager',
	'channel-cleaner',
];

export class ServiceHealthDashboard extends GuStack {
	constructor(scope: App, id: string, props: ServiceHealthDashboardProps) {
		super(scope, id, props);
		const { stage, workerSqsQueueName, dbInstanceId } = props;
		const period = Duration.minutes(5);
		const namespace = (service: string) => `Notifications/${stage}/${service}`;
		const metric = (
			metricNamespace: string,
			metricName: string,
			dimensionsMap: Record<string, string> = {},
			statistic = 'Sum',
			label = metricName,
		) =>
			new Metric({
				namespace: metricNamespace,
				metricName,
				dimensionsMap,
				statistic,
				label,
				period,
			});
		const sender = (name: string) =>
			platforms.map((platform) =>
				metric(namespace('workers'), name, { platform }, 'Sum', platform),
			);
		const search = (
			schema: string,
			terms: string,
			name: string,
			statistic: string,
			label: string,
		) =>
			new MathExpression({
				expression: `SEARCH('{${schema}} ${terms} MetricName="${name}"', '${statistic}', 300)`,
				period,
				label,
			});
		const harvesterPrefix = workerSqsQueueName.split('-Sqs-')[0];
		const queues = [
			{ label: 'Harvester queue', terms: `QueueName="${workerSqsQueueName}"` },
			{
				label: 'Harvester dead letters',
				terms: `QueueName: ${harvesterPrefix}-Dlq`,
			},
			{
				label: 'Sender queues',
				terms: `QueueName: SenderSqs AND QueueName: ${stage}`,
			},
		] as const;
		const queueMetrics = (name: string, statistic = 'Maximum') =>
			queues.map(({ label, terms }) =>
				search('AWS/SQS,QueueName', terms, name, statistic, label),
			);
		const queueSummary = (name: string, aggregate: string) =>
			queueMetrics(name).map(
				(query) =>
					new MathExpression({
						expression: `${aggregate}(${query.expression})`,
						period,
						label: query.label,
					}),
			);
		const apns = (outcomes: string[]) =>
			liveActivityFunctions.flatMap((service) =>
				outcomes.map((outcome) =>
					metric(
						namespace(`liveactivities-${service}`),
						'ApnsResponse',
						{ Outcome: outcome },
						'Sum',
						`${service}: ${outcome}`,
					),
				),
			);
		const football = ['goal-success', 'error'].map((name) =>
			metric(`${stage}/football-notifications`, name),
		);
		const registrations = metric(
			namespace('registration'),
			'SuccessfulRegistrationInsertion',
		);
		const breakingNews = metric(
			namespace('notification'),
			'BreakingNewsNotificationCount',
		);
		const dashboard = new Dashboard(this, 'Dashboard', {
			dashboardName: `notifications-${stage}`,
			defaultInterval: Duration.hours(24),
			periodOverride: PeriodOverride.INHERIT,
		});
		const section = (title: string, text: string) =>
			dashboard.addWidgets(
				new TextWidget({
					markdown: `## ${title}\n${text}`,
					width: 24,
					height: 2,
				}),
			);
		const graph = (title: string, metrics: IMetric[], axisLabel?: string) =>
			new GraphWidget({
				title,
				left: metrics,
				width: 8,
				height: 6,
				leftYAxis: { min: 0, label: axisLabel, showUnits: !axisLabel },
			});
		const value = (title: string, metrics: IMetric[], range = true) =>
			new SingleValueWidget({
				title,
				metrics,
				width: 4,
				height: 5,
				setPeriodToTimeRange: range,
			});

		section(
			'Are notifications getting out?',
			`${stage}: counts cover the selected range; queue depth and age show the latest five-minute sample. Quiet periods are normal.`,
		);
		const fixedAlarms = liveActivityFunctions.flatMap((service) => [
			`liveactivities-${service}-dlq-${stage}-alarm`,
			`liveactivities-apns-errors-${service}-${stage}`,
		]);
		dashboard.addWidgets(
			value('Breaking news sending', [breakingNews]),
			value('Sends succeeding by platform', sender('success')),
			value(
				'Messages waiting in queues',
				queueSummary('ApproximateNumberOfMessagesVisible', 'SUM'),
				false,
			),
			value('Registrations saved', [registrations]),
			value('Live Activities and football delivered', [
				...apns(['2xx']),
				metric(`${stage}/football-notifications`, 'goal-success'),
			]),
			new AlarmStatusWidget({
				title: 'Live Activities alarm status',
				width: 4,
				height: 5,
				alarms: fixedAlarms.map((name) =>
					Alarm.fromAlarmName(this, name, name),
				),
			}),
		);

		section(
			'How does this compare with last week?',
			'Hourly totals. Each right-hand chart shows the same 24-hour window seven days earlier; it stays fixed when the dashboard range changes.',
		);
		for (const [title, metrics] of [
			['Sends per hour by platform', sender('success')],
			['Registrations per hour', [registrations]],
			['Breaking news per hour', [breakingNews]],
		] as const) {
			for (const lastWeek of [false, true]) {
				dashboard.addWidgets(
					new GraphWidget({
						title: `${title}${lastWeek ? ' - last week' : ''}`,
						left: metrics.map((entry) =>
							entry.with({ period: Duration.hours(1) }),
						),
						period: Duration.hours(1),
						width: 12,
						height: 6,
						start: lastWeek ? '-P8D' : undefined,
						end: lastWeek ? '-P7D' : undefined,
						leftYAxis: { min: 0 },
					}),
				);
			}
		}

		section(
			'Where is the pipeline slowing down?',
			'Follow requests from the API through the harvester queue, harvester, sender queues and senders. Queue age is in seconds; dead letters need investigation.',
		);
		const functions = [
			{
				name: `mobile-notifications-harvester-ctr-${stage}`,
				label: 'Harvester',
			},
			...platforms.map((platform) => ({
				name: `mobile-notifications-workers-${platform}-sender-ctr-${stage}`,
				label: platform,
			})),
			{ name: `football-${stage}`, label: 'Football' },
		];
		const lambdaMetrics = (name: string, statistic = 'Sum') =>
			functions.map((fn) =>
				metric(
					'AWS/Lambda',
					name,
					{ FunctionName: fn.name },
					statistic,
					fn.label,
				),
			);
		dashboard.addWidgets(
			graph('API requests accepted', [
				breakingNews,
				metric(namespace('notification'), 'SuccessfulNewstandSend'),
			]),
			graph(
				'Harvester queue activity',
				[
					'NumberOfMessagesSent',
					'NumberOfMessagesReceived',
					'NumberOfMessagesDeleted',
				].map((name) =>
					search('AWS/SQS,QueueName', queues[0].terms, name, 'Sum', name),
				),
			),
			graph('Harvester runs and failures', [
				metric(
					'AWS/Lambda',
					'Invocations',
					{ FunctionName: `mobile-notifications-harvester-ctr-${stage}` },
					'Sum',
					'Harvester runs',
				),
				...platforms.map((platform) =>
					metric(
						namespace('harvester'),
						'failure',
						{ platform },
						'Sum',
						`${platform} failures`,
					),
				),
			]),
			graph(
				'Sender queue activity',
				[
					'NumberOfMessagesSent',
					'NumberOfMessagesReceived',
					'NumberOfMessagesDeleted',
				].map((name) =>
					search('AWS/SQS,QueueName', queues[2].terms, name, 'Sum', name),
				),
			),
			graph(
				'Sender runs by platform',
				lambdaMetrics('Invocations').slice(1, -1),
			),
			graph('Messages waiting or being processed', [
				...queueMetrics('ApproximateNumberOfMessagesVisible'),
				...queueMetrics('ApproximateNumberOfMessagesNotVisible'),
			]),
			graph(
				'Oldest message waiting',
				queueMetrics('ApproximateAgeOfOldestMessage'),
				'Seconds',
			),
			graph(
				'Send outcomes by platform',
				['success', 'failure', 'dryrun', 'total'].flatMap((name) =>
					sender(name).map((entry) =>
						entry.with({ label: `${entry.label}: ${name}` }),
					),
				),
			),
			graph('Live Activities responses and football outcomes', [
				...apns(['2xx', '4xx', '5xx', 'NetworkError']),
				...football,
			]),
		);

		section(
			'How long does delivery take?',
			'Sender token-delivery and processing metrics depend on feature flags being enabled and may be empty. API and registration latency do not depend on those flags.',
		);
		dashboard.addWidgets(
			graph(
				'Token delivery time - feature flag required',
				[
					search(
						`${namespace('workers')},platform,audienceSize`,
						'',
						'TokenDeliveryLatency',
						'Average',
						'By platform and audience size',
					),
				],
				'Seconds',
			),
			graph(
				'Sender processing time - feature flag required',
				[
					search(
						`${namespace('workers')},platform,type`,
						'',
						'worker.notificationProcessingTime',
						'Average',
						'By platform and notification type',
					),
				],
				'Milliseconds',
			),
			graph(
				'API and registration processing time',
				[
					metric(
						namespace('notification'),
						'NotificationAppProcessingTime',
						{},
						'Average',
					),
					metric(
						namespace('registration'),
						'RegistrationInsertionLatency',
						{},
						'Average',
					),
				],
				'Milliseconds',
			),
		);

		section(
			'What is failing?',
			'Lambda errors, throttles and duration help locate compute problems. Database and load balancer metrics show downstream health; application failures can occur without a Lambda error.',
		);
		dashboard.addWidgets(
			graph('Lambda errors', lambdaMetrics('Errors')),
			graph('Lambda throttles', lambdaMetrics('Throttles')),
			graph(
				'Lambda duration',
				lambdaMetrics('Duration', 'p95'),
				'Milliseconds',
			),
			graph('Registration writes failing', [
				metric(namespace('registration'), 'FailedRegistrationInsertion'),
			]),
			graph(
				'Database CPU use',
				[
					metric(
						'AWS/RDS',
						'CPUUtilization',
						{ DBInstanceIdentifier: dbInstanceId },
						'Average',
					),
				],
				'Percent',
			),
			graph('Database connections', [
				metric(
					'AWS/RDS',
					'DatabaseConnections',
					{ DBInstanceIdentifier: dbInstanceId },
					'Maximum',
				),
			]),
			graph(
				'Database free memory',
				[
					metric(
						'AWS/RDS',
						'FreeableMemory',
						{ DBInstanceIdentifier: dbInstanceId },
						'Minimum',
					),
				],
				'Bytes',
			),
			graph(
				'Database read and write latency',
				['ReadLatency', 'WriteLatency'].map((name) =>
					metric(
						'AWS/RDS',
						name,
						{ DBInstanceIdentifier: dbInstanceId },
						'Average',
					),
				),
				'Seconds',
			),
		);
		for (const service of ['Notification', 'Registration']) {
			const dimensions = {
				LoadBalancer: Fn.importValue(`${service}LoadBalancerFullName-${stage}`),
			};
			dashboard.addWidgets(
				graph(
					`${service} HTTP responses`,
					['2XX', '4XX', '5XX'].map((code) =>
						metric(
							'AWS/ApplicationELB',
							`HTTPCode_Target_${code}_Count`,
							dimensions,
						),
					),
				),
				graph(
					`${service} load balancer errors`,
					['4XX', '5XX'].map((code) =>
						metric(
							'AWS/ApplicationELB',
							`HTTPCode_ELB_${code}_Count`,
							dimensions,
						),
					),
				),
				graph(
					`${service} response time`,
					[
						metric(
							'AWS/ApplicationELB',
							'TargetResponseTime',
							dimensions,
							'p95',
						),
					],
					'Seconds',
				),
			);
		}
	}
}

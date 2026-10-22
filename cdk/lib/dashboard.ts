import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuStack } from '@guardian/cdk/lib/constructs/core';
import type { App } from 'aws-cdk-lib';
import { Duration, Fn } from 'aws-cdk-lib';
import type { IMetric } from 'aws-cdk-lib/aws-cloudwatch';
import {
	Alarm,
	AlarmStatusWidget,
	Column,
	Dashboard,
	GraphWidget,
	MathExpression,
	Metric,
	PeriodOverride,
	SingleValueWidget,
	TextWidget,
} from 'aws-cdk-lib/aws-cloudwatch';

export interface N10nServiceHealthDashboardProps extends GuStackProps {
	workerSqsQueueName: string;
	dbInstanceId: string;
}

const platforms = ['ios', 'android'];
const liveActivityFunctions = [
	'broadcast',
	'channel-manager',
	'channel-cleaner',
];

export class N10nServiceHealthDashboard extends GuStack {
	constructor(scope: App, id: string, props: N10nServiceHealthDashboardProps) {
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
		const platformFilter = `(${platforms.map((platform) => `platform="${platform}"`).join(' OR ')})`;
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
				terms: `QueueName: SenderSqs AND QueueName: ${stage} AND (${platforms.map((platform) => `QueueName: ${platform}SenderSqs`).join(' OR ')})`,
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
		const footballSent = metric(
			`${stage}/football-notifications`,
			'goal-success',
			{},
			'Sum',
			'Football notifications sent',
		);
		const footballFailures = metric(
			`${stage}/football-notifications`,
			'error',
			{},
			'Sum',
			'Send failures',
		);
		const football = [footballSent, footballFailures];
		const footballFailureRate = new MathExpression({
			expression: 'IF(sent + failed > 0, 100 * failed / (sent + failed))',
			usingMetrics: { sent: footballSent, failed: footballFailures },
			period,
			label: 'Send failures (%)',
		});
		const registrations = metric(
			namespace('registration'),
			'SuccessfulRegistrationInsertion',
		);
		const breakingNews = metric(
			namespace('notification'),
			'BreakingNewsNotificationCount',
		);
		const failedRegistrations = metric(
			namespace('registration'),
			'FailedRegistrationInsertion',
		);
		const acceptanceRates = platforms.map((platform, index) => {
			const successId = `success${index}`;
			const failureId = `failure${index}`;
			return new MathExpression({
				expression: `IF(${successId} + ${failureId} > 0, 100 * ${successId} / (${successId} + ${failureId}))`,
				usingMetrics: {
					[successId]: metric(namespace('workers'), 'success', { platform }),
					[failureId]: metric(namespace('workers'), 'failure', { platform }),
				},
				period,
				label: `${platform} (%)`,
			});
		});
		const total = (metrics: IMetric[], label: string, idPrefix: string) => {
			const usingMetrics = Object.fromEntries(
				metrics.map((entry, index) => [`${idPrefix}${index}`, entry]),
			);
			return new MathExpression({
				expression: `SUM([${Object.keys(usingMetrics).join(',')}])`,
				usingMetrics,
				period,
				label,
			});
		};
		const liveActivityFailureRate = new MathExpression({
			expression:
				'IF(accepted + failed > 0, 100 * failed / (accepted + failed))',
			usingMetrics: {
				accepted: total(apns(['2xx']), 'APNs accepted requests', 'accepted'),
				failed: total(
					apns(['4xx', '5xx', 'NetworkError']),
					'APNs failed requests',
					'failed',
				),
			},
			period,
			label: 'APNs failure rate (%)',
		});
		const liveActivityRecoveryMessages = total(
			liveActivityFunctions.map((service) =>
				metric(
					'AWS/SQS',
					'ApproximateNumberOfMessagesVisible',
					{ QueueName: `liveactivities-${service}-dlq-${stage}` },
					'Maximum',
				),
			),
			'Messages awaiting recovery',
			'recovery',
		);
		const dashboard = new Dashboard(this, 'Dashboard', {
			dashboardName: `n10n-service-health-dashboard-${stage}`,
			defaultInterval: Duration.days(7),
			periodOverride: PeriodOverride.INHERIT,
		});
		const section = (title: string, text: string) =>
			dashboard.addWidgets(
				new TextWidget({
					markdown: `# ${title}\n\n${text}`,
					width: 24,
					height: 3,
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
		const value = (
			title: string,
			metrics: IMetric[],
			range = true,
			width = 8,
			height = 6,
		) =>
			new SingleValueWidget({
				title,
				metrics,
				width,
				height,
				period,
				setPeriodToTimeRange: range,
			});

		section(
			'Service overview',
			`${stage} - Right now: counts and percentages cover the selected range; queue age, recovery messages and send time use the latest five-minute sample. Acceptance is not confirmed device delivery. Missing data is not a healthy result.\n\nNotification registration updates: Includes new registrations and changes to existing registrations.`,
		);
		const fixedAlarms = liveActivityFunctions.flatMap((service) => [
			`liveactivities-${service}-dlq-${stage}-alarm`,
			`liveactivities-apns-errors-${service}-${stage}`,
		]);
		dashboard.addWidgets(
			value('Breaking news sent', [
				breakingNews.with({ label: 'Breaking news sent' }),
			]),
			value(
				'Send time to APNs and FCM (p95)',
				platforms.map(
					(platform) =>
						new MathExpression({
							expression: `MAX(SEARCH('{${namespace('workers')},platform,audienceSize} platform="${platform}" MetricName="TokenDeliveryLatency"', 'p95', 300))`,
							period,
							label: `${platform}: slowest audience bucket p95 (seconds)`,
						}),
				),
				false,
			),
			value(
				'Sends accepted by APNs or FCM - excluding dry runs',
				acceptanceRates,
			),
			value('Notification registration updates', [
				registrations.with({ label: 'Updates saved' }),
				failedRegistrations.with({ label: 'Failed updates' }),
				new MathExpression({
					expression: 'IF(saved + failed > 0, 100 * failed / (saved + failed))',
					usingMetrics: { saved: registrations, failed: failedRegistrations },
					period,
					label: 'Failures %',
				}),
			]),
			value(
				'Notification queues',
				[
					...queueSummary('ApproximateAgeOfOldestMessage', 'MAX')
						.filter((entry, index) => index !== 1)
						.map((entry, index) =>
							entry.with({
								label: index === 0 ? 'Harvester queue age' : 'Sender queue age',
							}),
						)
						.reverse(),
					new MathExpression({
						expression: `SUM(SEARCH('{AWS/SQS,QueueName} ${queues[1].terms} MetricName="ApproximateNumberOfMessagesVisible"', 'Maximum', 300))`,
						period,
						label: 'Harvester DLQ',
					}),
				],
				false,
			),
			new Column(
				value('Live Activities health', [liveActivityFailureRate], true, 8, 3),
				value(
					'Messages awaiting recovery',
					[liveActivityRecoveryMessages],
					false,
					8,
					3,
				),
			),
		);

		section(
			'How does this compare with last week?',
			'Hourly totals. Each right-hand chart shows the previous seven-day window; it stays fixed when the dashboard range changes.',
		);
		for (const [title, metrics] of [
			['Sends per hour by platform', sender('success')],
			['Registrations per hour', [registrations]],
			['Breaking news per hour', [breakingNews]],
		] as const) {
			dashboard.addWidgets(
				...[false, true].map(
					(lastWeek) =>
						new GraphWidget({
							title: `${title}${lastWeek ? ' - last week' : ''}`,
							left: metrics.map((entry) =>
								entry.with({ period: Duration.hours(1) }),
							),
							period: Duration.hours(1),
							width: 12,
							height: 6,
							start: lastWeek ? '-P14D' : undefined,
							end: lastWeek ? '-P7D' : undefined,
							leftYAxis: { min: 0 },
						}),
				),
			);
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
		const pipeline = [
			value('1. Notification API', [breakingNews], false, 4),
			value(
				'2. Harvester queue age (seconds)',
				queueSummary('ApproximateAgeOfOldestMessage', 'MAX').slice(0, 1),
				false,
				4,
			),
			value(
				'3. Harvester runs and errors',
				[
					metric(
						'AWS/Lambda',
						'Invocations',
						{ FunctionName: `mobile-notifications-harvester-ctr-${stage}` },
						'Sum',
						'Runs',
					),
					metric(
						'AWS/Lambda',
						'Errors',
						{ FunctionName: `mobile-notifications-harvester-ctr-${stage}` },
						'Sum',
						'Errors',
					),
				],
				false,
				4,
			),
			value(
				'4. Sender queue age (seconds)',
				queueSummary('ApproximateAgeOfOldestMessage', 'MAX').slice(2),
				false,
				4,
			),
			value('5. Sender errors', lambdaMetrics('Errors').slice(1, -1), false, 4),
		];
		dashboard.addWidgets(
			...pipeline.flatMap((widget, index) => [
				widget,
				...(index < pipeline.length - 1
					? [new TextWidget({ markdown: '## >', width: 1, height: 6 })]
					: []),
			]),
		);

		section(
			'Existing alarms',
			'Only the six fixed-name Live Activities alarms appear here. This does not represent every alarm in n10n.',
		);
		dashboard.addWidgets(
			new AlarmStatusWidget({
				title: 'Live Activities dead letters and APNs errors',
				width: 24,
				height: 4,
				alarms: fixedAlarms.map((name) =>
					Alarm.fromAlarmName(this, name, name),
				),
			}),
		);

		section(
			'Pipeline detail',
			'Five-minute activity and outcomes. Queue receives can include retries; successful sends mean provider acceptance, not confirmed arrival on a device.',
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
			graph(
				'Live Activities requests and errors',
				apns(['2xx', '4xx', '5xx', 'NetworkError']),
			),
			graph('Football notifications sent and errors', football),
		);

		section(
			'How long does delivery take?',
			'Sender metrics exclude dry runs. Send time measures the time from receipt by the notification API to a successful handoff to APNs or FCM, not arrival on a device. It may be empty when there are no successful sends.',
		);
		dashboard.addWidgets(
			graph(
				'Send time to APNs and FCM by audience size',
				[
					search(
						`${namespace('workers')},platform,audienceSize`,
						platformFilter,
						'TokenDeliveryLatency',
						'Average',
						'By platform and audience size',
					),
				],
				'Seconds',
			),
			graph(
				'Sender processing time',
				[
					search(
						`${namespace('workers')},platform,type`,
						platformFilter,
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
			value('Football notification submission failures', [
				footballFailures,
				footballFailureRate,
			]),
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

import { join } from 'path';
import { GuPlayApp } from '@guardian/cdk';
import { AccessScope } from '@guardian/cdk/lib/constants';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import {
	GuDistributionBucketParameter,
	GuLoggingStreamNameParameter,
	GuStack,
	GuVpcParameter,
} from '@guardian/cdk/lib/constructs/core';
import { GuAllowPolicy } from '@guardian/cdk/lib/constructs/iam';
import type { App } from 'aws-cdk-lib';
import { Duration } from 'aws-cdk-lib';
import type { CfnAutoScalingGroup } from 'aws-cdk-lib/aws-autoscaling';
import {
	Alarm,
	ComparisonOperator,
	Metric,
	TreatMissingData,
} from 'aws-cdk-lib/aws-cloudwatch';
import { SnsAction } from 'aws-cdk-lib/aws-cloudwatch-actions';
import { Table } from 'aws-cdk-lib/aws-dynamodb';
import { InstanceClass, InstanceSize, InstanceType } from 'aws-cdk-lib/aws-ec2';
import { Topic } from 'aws-cdk-lib/aws-sns';
import { adjustCloudformationParameters } from './mobile-n10n-compatibility';
import { CfnInclude } from 'aws-cdk-lib/cloudformation-include';

export interface NotificationProps extends GuStackProps {
	/**
	 * The domain name for the notification service.
	 */
	domainName:
		| 'notification.notifications.guardianapis.com'
		| 'notification.notifications.code.dev-guardianapis.com';

	/**
	 * The granularity of EC2 instance metrics.
	 * Use '1Minute' for PROD and '5Minute' for CODE.
	 */
	instanceMetricGranularity: '1Minute' | '5Minute';

	/**
	 * The ARN of the SQS queue consumed by the workers (harvester).
	 */
	workerSqsQueueArn: string;

	/**
	 * The name of the S3 bucket storing persisted topic subscription counts.
	 */
	s3TopicCountBucket: string;

	/**
	 * The ARN of the SNS topic for CloudWatch alarms.
	 */
	alarmTopicArn: string;

	/**
	 * Minimum size of the autoscaling group.
	 */
	minAsgSize: number;

	/**
	 * Maximum size of the autoscaling group.
	 */
	maxAsgSize: number;

	/**
	 * The daily newsstand push count threshold for the alarm.
	 * Set to 0 for CODE (no alarm), 1 for PROD.
	 */
	dailyNewsstandPushCount: number;
}

export class Notification extends GuStack {
	constructor(scope: App, id: string, props: NotificationProps) {
		super(scope, id, props);

		const { stack, stage, region, account } = this;
		const {
			domainName,
			instanceMetricGranularity,
			workerSqsQueueArn,
			s3TopicCountBucket,
			alarmTopicArn,
			minAsgSize,
			maxAsgSize,
			dailyNewsstandPushCount,
		} = props;

        const yamlTemplateFilePath = join(
            __dirname,
            '../../notification/conf/notification.yaml',
        );
        new CfnInclude(this, 'YamlTemplate', {
            templateFile: yamlTemplateFilePath,
        });

		const app = 'notification';

		const reportsTable = Table.fromTableName(
			this,
			'ReportsTable',
			`mobile-notifications-reports-${stage}`,
		);

		const scheduleTable = Table.fromTableName(
			this,
			'ScheduleTable',
			`schedule-${stage}-mobile-notifications`,
		);

		const alarmTopic = Topic.fromTopicArn(this, 'AlarmTopic', alarmTopicArn);

		const { autoScalingGroup, loadBalancer } = new GuPlayApp(this, {
			access: {
				scope: AccessScope.PUBLIC,
			},
			app,
			certificateProps: {
				domainName,
			},
			instanceMetricGranularity,
			instanceType: InstanceType.of(InstanceClass.T4G, InstanceSize.SMALL),
			monitoringConfiguration: {
				http5xxAlarm: {
					tolerated5xxPercentage: 0,
					numberOfMinutesAboveThresholdBeforeAlarm: 10,
				},
				unhealthyInstancesAlarm: true,
				snsTopicName: 'mobile-server-side',
			},
			roleConfiguration: {
				additionalPolicies: [
					// S3 access for topic count bucket
					new GuAllowPolicy(this, 'S3TopicCountAccess', {
						actions: ['s3:GetObject'],
						resources: [`arn:aws:s3:::${s3TopicCountBucket}/*`],
					}),
					// EC2 describe permissions
					new GuAllowPolicy(this, 'EC2DescribeAccess', {
						actions: ['ec2:DescribeTags', 'ec2:DescribeInstances'],
						resources: ['*'],
					}),
					// CloudWatch and Logs permissions
					new GuAllowPolicy(this, 'CloudWatchLogsAccess', {
						actions: ['cloudwatch:*', 'logs:*'],
						resources: ['*'],
					}),
					// Autoscaling describe permissions
					new GuAllowPolicy(this, 'AutoscalingDescribeAccess', {
						actions: [
							'autoscaling:DescribeAutoScalingInstances',
							'autoscaling:DescribeAutoScalingGroups',
						],
						resources: ['*'],
					}),
					// Kinesis permissions for log aggregation
					new GuAllowPolicy(this, 'KinesisAccess', {
						actions: [
							'kinesis:PutRecord',
							'kinesis:PutRecords',
							'kinesis:DescribeStream',
						],
						resources: [
							`arn:aws:kinesis:${region}:${account}:stream/mobile-log-aggregation-${stage}`,
						],
					}),
					// DynamoDB access for reports table
					new GuAllowPolicy(this, 'DynamoDbReportsAccess', {
						actions: ['dynamodb:*'],
						resources: [
							reportsTable.tableArn,
							`${reportsTable.tableArn}/index/*`,
						],
					}),
					// DynamoDB access for schedule table
					new GuAllowPolicy(this, 'DynamoDbScheduleAccess', {
						actions: ['dynamodb:*'],
						resources: [
							scheduleTable.tableArn,
							`${scheduleTable.tableArn}/index/due_epoch_s_and_sent`,
						],
					}),
					// SSM parameter store access (custom path)
					new GuAllowPolicy(this, 'CustomParameterStoreAccess', {
						actions: ['ssm:GetParametersByPath'],
						resources: [
							`arn:aws:ssm:${region}:${account}:parameter/notifications/${stage}/${stack}`,
						],
					}),
					// SQS access for worker queues
					new GuAllowPolicy(this, 'WorkerSqsAccess', {
						actions: ['sqs:SendMessage'],
						resources: [workerSqsQueueArn],
					}),
					// SQS access for SLO monitoring queue
					new GuAllowPolicy(this, 'SloSqsAccess', {
						actions: ['sqs:SendMessage'],
						resources: [
							`arn:aws:sqs:${region}:${account}:notifications-slo-monitoring-${stage}`,
						],
					}),
				],
			},
			scaling: {
				minimumInstances: minAsgSize,
				maximumInstances: maxAsgSize,
			},
			userData: {
				distributable: {
					fileName: `${app}_1.0-latest_all.deb`,
					executionStatement: `dpkg -i /${app}/${app}_1.0-latest_all.deb`,
				},
			},
			// Match existing healthcheck settings
			healthcheck: {
				healthyThresholdCount: 2,
				interval: Duration.seconds(30),
				timeout: Duration.seconds(10),
				unhealthyThresholdCount: 10,
			},
			applicationLogging: {
				enabled: true,
				systemdUnitName: app,
			},
		});

		// Match existing healthcheck grace period
		const cfnAsg = autoScalingGroup.node.defaultChild as CfnAutoScalingGroup;
		cfnAsg.healthCheckGracePeriod = Duration.seconds(400).toSeconds();

		adjustCloudformationParameters(this);

		// This is necessary whilst dual-stacking because there is already a parameter called VpcId in the YAML template
		// Once the YAML template has been removed we should be able to drop this override
		const vpcParameter = GuVpcParameter.getInstance(this);
		vpcParameter.overrideLogicalId('GuCdkVpcId');

		// Similarly, override LoggingStreamName to avoid conflict with YAML template
		const loggingStreamParameter =
			GuLoggingStreamNameParameter.getInstance(this);
		loggingStreamParameter.overrideLogicalId('GuCdkLoggingStreamName');

		// Similarly, override DistributionBucketName to avoid conflict with YAML template's DistBucket
		const distBucketParameter = GuDistributionBucketParameter.getInstance(this);
		distBucketParameter.overrideLogicalId('GuCdkDistributionBucketName');

		// 5XX Backend Alarm (HTTPCode_Target_5XX_Count for ALB)
		const httpCodeTarget5xxAlarm = new Alarm(this, 'HttpCodeTarget5xxAlarm', {
			alarmDescription: `Triggers if notification errors in ${stage} with 5XX. <<<Runbook|https://docs.google.com/document/d/1aJMytnPGeWH8YLpD2_66doxqyr8dPvAVonYIOG-zmOA>>>`,
			metric: new Metric({
				namespace: 'AWS/ApplicationELB',
				metricName: 'HTTPCode_Target_5XX_Count',
				dimensionsMap: {
					LoadBalancer: loadBalancer.loadBalancerFullName,
				},
				statistic: 'Sum',
				period: Duration.seconds(60),
			}),
			comparisonOperator: ComparisonOperator.GREATER_THAN_THRESHOLD,
			threshold: 0,
			evaluationPeriods: 10,
			treatMissingData: TreatMissingData.NOT_BREACHING,
		});
		httpCodeTarget5xxAlarm.addAlarmAction(new SnsAction(alarmTopic));
		httpCodeTarget5xxAlarm.addOkAction(new SnsAction(alarmTopic));

		// 5XX ELB Alarm (HTTPCode_ELB_5XX_Count for ALB)
		const httpCodeElb5xxAlarm = new Alarm(this, 'HttpCodeElb5xxAlarm', {
			alarmDescription: `Triggers if load balancer errors in ${stage} with 5XX. <<<Runbook|https://docs.google.com/document/d/1aJMytnPGeWH8YLpD2_66doxqyr8dPvAVonYIOG-zmOA>>>`,
			metric: new Metric({
				namespace: 'AWS/ApplicationELB',
				metricName: 'HTTPCode_ELB_5XX_Count',
				dimensionsMap: {
					LoadBalancer: loadBalancer.loadBalancerFullName,
				},
				statistic: 'Sum',
				period: Duration.seconds(60),
			}),
			comparisonOperator: ComparisonOperator.GREATER_THAN_THRESHOLD,
			threshold: 0,
			evaluationPeriods: 10,
			treatMissingData: TreatMissingData.NOT_BREACHING,
		});
		httpCodeElb5xxAlarm.addAlarmAction(new SnsAction(alarmTopic));
		httpCodeElb5xxAlarm.addOkAction(new SnsAction(alarmTopic));

		// Newsstand Sent Alarm - only meaningful for PROD (threshold > 0)
		if (dailyNewsstandPushCount > 0) {
			const newsstandSentAlarm = new Alarm(this, 'NewsstandSentAlarm', {
				alarmDescription: `Triggers if less than ${dailyNewsstandPushCount} daily edition notification was sent for ${stage} in the last 24 hours.`,
				metric: new Metric({
					namespace: `Notifications/${stage}/notification`,
					metricName: 'SuccessfulNewstandSend',
					statistic: 'Sum',
					period: Duration.seconds(86400), // 24 hours
				}),
				comparisonOperator: ComparisonOperator.LESS_THAN_THRESHOLD,
				threshold: dailyNewsstandPushCount,
				evaluationPeriods: 1,
				treatMissingData: TreatMissingData.BREACHING,
			});
			newsstandSentAlarm.addAlarmAction(new SnsAction(alarmTopic));
			newsstandSentAlarm.addOkAction(new SnsAction(alarmTopic));
		}
	}
}

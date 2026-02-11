import { GuPlayApp } from '@guardian/cdk';
import { AccessScope } from '@guardian/cdk/lib/constants';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import {
	GuDistributionBucketParameter,
	GuLoggingStreamNameParameter,
	GuStack,
	GuStringParameter,
	GuVpcParameter,
} from '@guardian/cdk/lib/constructs/core';
import { GuCname } from '@guardian/cdk/lib/constructs/dns';
import { GuAllowPolicy } from '@guardian/cdk/lib/constructs/iam';
import type { App } from 'aws-cdk-lib';
import { Duration } from 'aws-cdk-lib';
import type { CfnAutoScalingGroup } from 'aws-cdk-lib/aws-autoscaling';
import { Table } from 'aws-cdk-lib/aws-dynamodb';
import { InstanceClass, InstanceSize, InstanceType } from 'aws-cdk-lib/aws-ec2';
import { adjustCloudformationParameters } from './mobile-n10n-compatibility';

export interface NotificationProps extends GuStackProps {
	domainName:
		| 'notification.notifications.guardianapis.com'
		| 'notification.notifications.code.dev-guardianapis.com';
	instanceMetricGranularity: '1Minute' | '5Minute';
	/**
	 * The ARN of the SQS queue consumed by the workers (harvester).
	 */
	workerSqsQueueName: string;
	sloMonitoringQueueName: string;
	minAsgSize: number;
}

export class Notification extends GuStack {
	constructor(scope: App, id: string, props: NotificationProps) {
		super(scope, id, props);

		const { stack, stage, region, account } = this;
		const {
			domainName,
			instanceMetricGranularity,
			workerSqsQueueName,
			sloMonitoringQueueName,
			minAsgSize,
		} = props;

		const app = 'notification';

		const s3TopicCountBucketName = `/notifications/${this.stage}/${this.stack}/notifications.topicCounts.bucket`;
		const s3TopicCountBucket = new GuStringParameter(this, 'TopicCountBucket', {
			description:
				'SSM parameter containing the bucket name containing topic counts',
			default: s3TopicCountBucketName,
			allowedValues: [s3TopicCountBucketName],
			fromSSM: true,
		}).valueAsString;

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
					//in-line bucket name?
					new GuAllowPolicy(this, 'S3TopicCountAccess', {
						actions: ['s3:GetObject'],
						resources: [`arn:aws:s3:::${s3TopicCountBucket}/*`],
					}),
					// TODO: is this needed?? this include putting metrics
					new GuAllowPolicy(this, 'CloudWatchLogsAccess', {
						actions: ['cloudwatch:*', 'logs:*'],
						resources: ['*'],
					}),
					// TODO: are these permissions too broad? do we need to include delete?
					new GuAllowPolicy(this, 'DynamoDbReportsAccess', {
						actions: ['dynamodb:*'],
						resources: [
							reportsTable.tableArn,
							`${reportsTable.tableArn}/index/*`,
						],
					}),
					new GuAllowPolicy(this, 'DynamoDbScheduleAccess', {
						actions: ['dynamodb:*'],
						resources: [
							scheduleTable.tableArn,
							`${scheduleTable.tableArn}/index/due_epoch_s_and_sent`,
						],
					}),
					// Unfortunately the path used by the app does not match the path the pattern expects
					new GuAllowPolicy(this, 'CustomParameterStoreAccess', {
						actions: ['ssm:GetParametersByPath'],
						resources: [
							`arn:aws:ssm:${region}:${account}:parameter/notifications/${stage}/${stack}`,
						],
					}),
					new GuAllowPolicy(this, 'WorkerSqsAccess', {
						actions: ['sqs:SendMessage'],
						resources: [
							`arn:aws:sqs:${region}:${account}:${workerSqsQueueName}`,
						],
					}),
					new GuAllowPolicy(this, 'SloSqsAccess', {
						actions: ['sqs:SendMessage'],
						resources: [
							`arn:aws:sqs:${region}:${account}:${sloMonitoringQueueName}`,
						],
					}),
				],
			},
			scaling: {
				minimumInstances: minAsgSize,
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

		new GuCname(this, 'DnsRecordForNotification', {
			app,
			domainName: props.domainName,
			resourceRecord: loadBalancer.loadBalancerDnsName,
			ttl: Duration.seconds(3600),
		});

		autoScalingGroup.scaleOnCpuUtilization('CpuScalingPolicy', {
			targetUtilizationPercent: 20,
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
	}
}

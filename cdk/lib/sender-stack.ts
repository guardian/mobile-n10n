import { GuAutoScalingGroup } from '@guardian/cdk/lib/constructs/autoscaling';
import { GuAlarm } from '@guardian/cdk/lib/constructs/cloudwatch';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { AppIdentity, GuStack } from '@guardian/cdk/lib/constructs/core';
import {
	GuSecurityGroup,
	GuVpc,
	SubnetType,
} from '@guardian/cdk/lib/constructs/ec2';
import {
	GuAllowPolicy,
	GuInstanceRole,
} from '@guardian/cdk/lib/constructs/iam';
import type { GuAsgCapacity } from '@guardian/cdk/lib/types';
import type { App } from 'aws-cdk-lib';
import { CfnOutput, Duration } from 'aws-cdk-lib';
import { HealthCheck, ScalingEvents } from 'aws-cdk-lib/aws-autoscaling';
import {
	ComparisonOperator,
	MathExpression,
	Metric,
	TreatMissingData,
} from 'aws-cdk-lib/aws-cloudwatch';
import { SnsAction } from 'aws-cdk-lib/aws-cloudwatch-actions';
import type { InstanceType } from 'aws-cdk-lib/aws-ec2';
import { PolicyStatement } from 'aws-cdk-lib/aws-iam';
import { Topic } from 'aws-cdk-lib/aws-sns';
import { Queue } from 'aws-cdk-lib/aws-sqs';
import {
	ParameterDataType,
	ParameterTier,
	StringParameter,
} from 'aws-cdk-lib/aws-ssm';

interface SenderStackProps extends GuStackProps {
	appName: string;
	asgCapacity: GuAsgCapacity;
	instanceType: InstanceType;
	targetCpuUtilization: number;
	notificationSnsTopic: string;
	alarmSnsTopic: string;
	alarmEnabled: boolean;
	cleanerQueueArn: string;
}

export class SenderWorkerStack extends GuStack {
	constructor(scope: App, id: string, props: SenderStackProps) {
		super(scope, id, props);

		const sqsMessageVisibilityTimeout = Duration.seconds(100);
		const sqsMessageRetentionPeriod = Duration.hours(1);
		const sqsMessageRetryCount = 5;
		const defaultVpcSecurityGroup = 'sg-85829de7';
		const runbookCopy =
			'<<<Runbook|https://docs.google.com/document/d/1aJMytnPGeWH8YLpD2_66doxqyr8dPvAVonYIOG-zmOA>>>';
		const alarmSnsTopic = Topic.fromTopicArn(
			this,
			`SnsTopicForOkAction`,
			`arn:aws:sns:${this.region}:${this.account}:${props.alarmSnsTopic}`,
		);
		const oldestMessageAgeThreshold = 700; // in seconds
		const messagesInFlightThreshold = 400;
		const toleratedErrorPercentage = 1;
		const processedPercentageThreshold = 50;
		const processedPercentageEvalPeriod = 3;

		const vpc = GuVpc.fromIdParameter(
			this,
			AppIdentity.suffixText({ app: props.appName }, 'VPC'),
		);
		const distributionRole = new GuInstanceRole(this, {
			app: props.appName,
			additionalPolicies: [
				// the parameter path used by MAPI is not covered
				// by the policy included in GuCDK pattern
				new GuAllowPolicy(this, 'GetParametersByPath', {
					resources: [
						`arn:aws:ssm:${this.region}:${this.account}:parameter/notifications/${props.stage}/workers`,
						`arn:aws:ssm:${this.region}:${this.account}:parameter/notifications/${props.stage}/ec2workers`,
					],
					actions: ['ssm:GetParametersByPath'],
				}),
				new GuAllowPolicy(this, 'SendToCleanerQueue', {
					resources: [props.cleanerQueueArn],
					actions: ['sqs:SendMessage'],
				}),
				new GuAllowPolicy(this, 'PutCloudwatchMetric', {
					resources: ['*'],
					actions: ['cloudwatch:PutMetricData'],
				}),
			],
		});

		const autoScalingGroup = new GuAutoScalingGroup(this, 'AutoScalingGroup', {
			app: props.appName,
			vpc,
			instanceType: props.instanceType,
			minimumInstances: props.asgCapacity.minimumInstances,
			maximumInstances: props.asgCapacity.maximumInstances,
			role: distributionRole,
			healthCheck: HealthCheck.elb({ grace: Duration.minutes(5) }),
			userData: `#!/bin/bash -ev
aws --region ${this.region} s3 cp s3://mobile-dist/${this.stack}/${props.stage}/${props.appName}/${props.appName}_1.0-latest_all.deb /tmp
dpkg -i /tmp/${props.appName}_1.0-latest_all.deb
/opt/aws-kinesis-agent/configure-aws-kinesis-agent ${this.region} mobile-log-aggregation-${this.stage} /var/log/${props.appName}/application.log`,
			vpcSubnets: {
				subnets: GuVpc.subnetsFromParameter(this, {
					type: SubnetType.PRIVATE,
					app: props.appName,
				}),
			},
			notifications: [
				{
					topic: Topic.fromTopicArn(
						this,
						'AutoscalingNotifications',
						props.notificationSnsTopic,
					),
					scalingEvents: ScalingEvents.ERRORS,
				},
			],
			additionalSecurityGroups: [
				GuSecurityGroup.fromSecurityGroupId(
					this,
					'DefaultVpcSecurityGroup',
					defaultVpcSecurityGroup,
				),
			],
		});
		autoScalingGroup.scaleOnCpuUtilization('CpuScalingPolicy', {
			targetUtilizationPercent: props.targetCpuUtilization,
		});

		const createSqsWithAlarms = (platformName: string, paramPrefix: string) => {
			const senderDlq = new Queue(this, `SenderDlq-${platformName}`);
			const senderSqs = new Queue(this, `SenderSqs-${platformName}`, {
				visibilityTimeout: sqsMessageVisibilityTimeout,
				retentionPeriod: sqsMessageRetentionPeriod,
				deadLetterQueue: {
					queue: senderDlq,
					maxReceiveCount: sqsMessageRetryCount,
				},
			});

			// grant the EC2 access to the queue
			distributionRole.addToPolicy(
				new PolicyStatement({
					actions: ['sqs:*'],
					resources: [senderSqs.queueArn],
				}),
			);

			// this advertises the name of the sender queue to the harvester app
			new StringParameter(this, `SenderQueueSSMParameter-${platformName}`, {
				parameterName: `/notifications/${this.stage}/workers/harvester/${paramPrefix}SqsEc2Url`,
				simpleName: false,
				stringValue: senderSqs.queueUrl,
				tier: ParameterTier.STANDARD,
				dataType: ParameterDataType.TEXT,
			});

			//this advertises the url of the ec2 worker queue
			new StringParameter(
				this,
				`SenderWorkerQueueEc2SSMParameter-${platformName}`,
				{
					parameterName: `/notifications/${this.stage}/ec2workers/${platformName}/sqsEc2Url`,
					simpleName: false,
					stringValue: senderSqs.queueUrl,
					tier: ParameterTier.STANDARD,
					dataType: ParameterDataType.TEXT,
				},
			);

			//this advertises the name of the ec2 worker queue
			new StringParameter(
				this,
				`SenderWorkerQueueNameEc2SSMParameter-${platformName}`,
				{
					parameterName: `/notifications/${this.stage}/ec2workers/${platformName}/sqsEc2Name`,
					simpleName: false,
					stringValue: senderSqs.queueName,
					tier: ParameterTier.STANDARD,
					dataType: ParameterDataType.TEXT,
				},
			);

			//this advertises the url of the worker queue
			new StringParameter(
				this,
				`SenderWorkerQueueSSMParameter-${platformName}`,
				{
					parameterName: `/notifications/${this.stage}/workers/${platformName}/sqsUrl`,
					simpleName: false,
					stringValue: senderSqs.queueUrl,
					tier: ParameterTier.STANDARD,
					dataType: ParameterDataType.TEXT,
				},
			);

			//this advertises the name of the worker queue
			new StringParameter(
				this,
				`SenderWorkerQueueNameSSMParameter-${platformName}`,
				{
					parameterName: `/notifications/${this.stage}/workers/${platformName}/sqsName`,
					simpleName: false,
					stringValue: senderSqs.queueName,
					tier: ParameterTier.STANDARD,
					dataType: ParameterDataType.TEXT,
				},
			);

			const oldestMessageAgeAlarm = new GuAlarm(
				this,
				`MessageAgeAlarm-${platformName}`,
				{
					app: props.appName,
					actionsEnabled: props.alarmEnabled,
					alarmDescription: `Triggers if the age of the oldest message exceeds the threshold. ${runbookCopy}`,
					alarmName: `${props.appName}-${this.stage}-MessageAge-${platformName}`,
					comparisonOperator: ComparisonOperator.GREATER_THAN_THRESHOLD,
					evaluationPeriods: 1,
					metric: senderSqs.metricApproximateAgeOfOldestMessage({
						period: Duration.seconds(60),
						statistic: 'Maximum',
					}),
					snsTopicName: props.alarmSnsTopic,
					threshold: oldestMessageAgeThreshold,
					treatMissingData: TreatMissingData.NOT_BREACHING,
				},
			);
			oldestMessageAgeAlarm.addOkAction(new SnsAction(alarmSnsTopic));

			const messagesInFlightAlarm = new GuAlarm(
				this,
				`messagesInFlightAlarm-${platformName}`,
				{
					app: props.appName,
					actionsEnabled: props.alarmEnabled,
					alarmDescription: `Triggers if the number of messages in flight exceeds the threshold. ${runbookCopy}`,
					alarmName: `${props.appName}-${this.stage}-MessageInFlight-${platformName}`,
					comparisonOperator: ComparisonOperator.GREATER_THAN_THRESHOLD,
					evaluationPeriods: 1,
					metric: senderSqs.metricApproximateNumberOfMessagesNotVisible({
						period: Duration.seconds(60),
						statistic: 'Maximum',
					}),
					snsTopicName: props.alarmSnsTopic,
					threshold: messagesInFlightThreshold,
					treatMissingData: TreatMissingData.NOT_BREACHING,
				},
			);
			messagesInFlightAlarm.addOkAction(new SnsAction(alarmSnsTopic));

			const getMetric = (metricName: string, statistic: string = 'Sum') =>
				new Metric({
					namespace: `Notifications/${this.stage}/ec2workers`,
					metricName: metricName,
					period: Duration.minutes(5),
					statistic: statistic,
					dimensionsMap: { platform: platformName },
					label: `${metricName}-${platformName}`,
				});
			const failurePercentageExpr = new MathExpression({
				expression: '100*m1/(m2-m3)',
				usingMetrics: {
					m1: getMetric('failure'),
					m2: getMetric('total'),
					m3: getMetric('dryrun'),
				},
				label: `Failure % of EC2 Sender - ${platformName}`,
				period: Duration.minutes(5),
			});
			const failurePercentageAlarm = new GuAlarm(
				this,
				`failurePercentage-${platformName}`,
				{
					app: props.appName,
					actionsEnabled: props.alarmEnabled,
					metric: failurePercentageExpr,
					treatMissingData: TreatMissingData.NOT_BREACHING,
					threshold: toleratedErrorPercentage,
					comparisonOperator: ComparisonOperator.GREATER_THAN_THRESHOLD,
					evaluationPeriods: 1,
					alarmName: `${props.appName}-${props.stage}-failure-${platformName}`,
					alarmDescription: `EC2 sender exceeds ${toleratedErrorPercentage}% error percentage`,
					snsTopicName: props.alarmSnsTopic,
				},
			);
			failurePercentageAlarm.addOkAction(new SnsAction(alarmSnsTopic));

			const processedPercentageExpr = new MathExpression({
				expression: '100*m1/m2',
				usingMetrics: {
					m1: getMetric('total', 'SampleCount'),
					m2: senderSqs.metricNumberOfMessagesReceived({
						period: Duration.minutes(5),
						statistic: 'Sum',
					}),
				},
				label: `Processed % of SQS messages by EC2 Sender - ${platformName}`,
				period: Duration.minutes(5),
			});
			const processedPercentageAlarm = new GuAlarm(
				this,
				`processed-${platformName}`,
				{
					app: props.appName,
					actionsEnabled: props.alarmEnabled,
					metric: processedPercentageExpr,
					treatMissingData: TreatMissingData.NOT_BREACHING,
					threshold: processedPercentageThreshold,
					comparisonOperator: ComparisonOperator.LESS_THAN_THRESHOLD,
					evaluationPeriods: processedPercentageEvalPeriod,
					alarmName: `${props.appName}-${props.stage}-processed-${platformName}`,
					alarmDescription: `EC2 sender processed less than ${processedPercentageThreshold}% messages`,
					snsTopicName: props.alarmSnsTopic,
				},
			);
			processedPercentageAlarm.addOkAction(new SnsAction(alarmSnsTopic));
			return senderSqs;
		};

		const senderQueueArns: string[] = [
			createSqsWithAlarms('ios', 'iosLive').queueArn,
			createSqsWithAlarms('android', 'androidLive').queueArn,
			createSqsWithAlarms('ios-edition', 'iosEdition').queueArn,
			createSqsWithAlarms('android-edition', 'androidEdition').queueArn,
			createSqsWithAlarms('android-beta', 'androidBeta').queueArn,
		];

		/*
		 * Here, we export the list of sender queue ARNs so that it can be used in other stacks,
		 * for example, Harvester needs to give itself permission to write to these queues.
		 */
		new CfnOutput(this, 'NotificationEc2SenderWorkerQueueArns', {
			exportName: 'NotificationEc2SenderWorkerQueueArns-' + this.stage,
			value: senderQueueArns.join(','),
		});
	}
}

import { GuAutoScalingGroup } from '@guardian/cdk/lib/constructs/autoscaling';
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
	cleanerQueueArn: string;
}

export class SenderWorkerStack extends GuStack {
	constructor(scope: App, id: string, props: SenderStackProps) {
		super(scope, id, props);

		const sqsMessageVisibilityTimeout = Duration.seconds(100);
		const sqsMessageRetentionPeriod = Duration.hours(1);
		const sqsMessageRetryCount = 5;
		const defaultVpcSecurityGroup = 'sg-85829de7';

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
/opt/aws-kinesis-agent/configure-aws-kinesis-agent ${this.region} mobile-log-aggregation-${this.stage} /var/log/${props.appName}/${props.appName}.log`,
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

		const createSqs = (platformName: string, paramPrefix: string) => {
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

			return senderSqs;
		};

		const senderQueueArns: string[] = [
			createSqs('ios', 'iosLive').queueArn,
			createSqs('android', 'androidLive').queueArn,
			createSqs('ios-edition', 'iosEdition').queueArn,
			createSqs('android-edition', 'androidEdition').queueArn,
			createSqs('android-beta', 'androidBeta').queueArn,
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

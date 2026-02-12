import { GuEc2App } from '@guardian/cdk';
import { AccessScope } from '@guardian/cdk/lib/constants';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuParameter, GuStack } from '@guardian/cdk/lib/constructs/core';
import { GuCname } from '@guardian/cdk/lib/constructs/dns';
import { GuAllowPolicy } from '@guardian/cdk/lib/constructs/iam';
import { Duration } from 'aws-cdk-lib';
import type { App } from 'aws-cdk-lib';
import {
	ComparisonOperator,
	TreatMissingData,
} from 'aws-cdk-lib/aws-cloudwatch';
import { SnsAction } from 'aws-cdk-lib/aws-cloudwatch-actions';
import {
	InstanceClass,
	InstanceSize,
	InstanceType,
	SecurityGroup,
} from 'aws-cdk-lib/aws-ec2';
import { HttpCodeTarget } from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import { Topic } from 'aws-cdk-lib/aws-sns';
import { adjustCloudformationParameters } from './mobile-n10n-compatibility';

export interface RegistrationProps extends GuStackProps {
	app: string;
	domainName:
		| 'notifications.guardianapis.com'
		| 'notifications.code.dev-guardianapis.com';
	instanceMetricGranularity: '1Minute' | '5Minute';
	minAsgSize: number;
	maxAsgSize?: number;
	low2xxAlarms: Array<{ period: Duration; threshold: number }>;
}

export class Registration extends GuStack {
	constructor(scope: App, id: string, props: RegistrationProps) {
		super(scope, id, props);
		const {
			app,
			stage,
			stack,
			domainName,
			instanceMetricGranularity,
			minAsgSize,
			maxAsgSize,
			low2xxAlarms,
		} = props;

		const { account, region } = this;

		const { autoScalingGroup, loadBalancer } = new GuEc2App(this, {
			app,
			access: {
				scope: AccessScope.PUBLIC,
			},
			applicationPort: 9000,
			applicationLogging: {
				enabled: true,
			},
			certificateProps: {
				domainName,
			},
			instanceMetricGranularity,
			instanceType: InstanceType.of(InstanceClass.T4G, InstanceSize.SMALL),

			// This matches the YAML stack (i.e. there is no 5XX alarm).
			monitoringConfiguration: { noMonitoring: true },

			scaling: { minimumInstances: minAsgSize, maximumInstances: maxAsgSize },

			userData: {
				distributable: {
					fileName: `${app}_1.0-latest_all.deb`,
					executionStatement: `dpkg -i /${app}/${app}_1.0-latest_all.deb`,
				},
			},

			roleConfiguration: {
				additionalPolicies: [
					// Unfortunately the path used by the app does not match the path the pattern expects
					new GuAllowPolicy(this, 'CustomParameterStoreAccess', {
						actions: ['ssm:GetParametersByPath'],
						resources: [
							`arn:aws:ssm:${region}:${account}:parameter/notifications/${stage}/${stack}`,
						],
					}),

					// TODO: is this needed?
					new GuAllowPolicy(this, 'CloudWatchLogsAccess', {
						actions: ['cloudwatch:*', 'logs:*'],
						resources: ['*'],
					}),
				],
			},
		});

		const databaseAccessParamPath = `/${stage}/${stack}/registrations-db/postgres-access-security-group`;
		const databaseSecurityGroupId = new GuParameter(
			this,
			'RegistrationsDatabaseAccessSecurityGroup',
			{
				default: databaseAccessParamPath,
				allowedValues: [databaseAccessParamPath],
				type: 'AWS::EC2::SecurityGroup::Id',
				fromSSM: true,
				description:
					'SSM parameter path for the security group that allows access to the registrations database',
			},
		).valueAsString;

		autoScalingGroup.connections.addSecurityGroup(
			SecurityGroup.fromSecurityGroupId(
				this,
				'DatabaseAccessSecurityGroup',
				databaseSecurityGroupId,
			),
		);

		autoScalingGroup.scaleOnCpuUtilization('CpuScalingPolicy', {
			targetUtilizationPercent: 20,
		});

		const runbookCopy =
			'<<<Runbook|https://docs.google.com/document/d/1aJMytnPGeWH8YLpD2_66doxqyr8dPvAVonYIOG-zmOA>>>';

		const alarmTopic = Topic.fromTopicArn(
			this,
			'AlarmTopic',
			`arn:aws:sns:${region}:${account}:mobile-server-side`,
		);
		const snsAction = new SnsAction(alarmTopic);

		low2xxAlarms.forEach(({ period, threshold }) => {
			const humanPeriod = period.toHumanString();
			const alarm = loadBalancer.metrics
				.httpCodeTarget(HttpCodeTarget.TARGET_2XX_COUNT, {
					period,
					statistic: 'Sum',
				})
				.createAlarm(this, `Low2XXIn${humanPeriod}`, {
					actionsEnabled: true,
					alarmDescription: `Triggers if load balancer in ${stage} does not have enough 200s in ${humanPeriod}. ${runbookCopy}`,
					comparisonOperator: ComparisonOperator.LESS_THAN_THRESHOLD,
					evaluationPeriods: 1,
					threshold,
					treatMissingData: TreatMissingData.BREACHING,
				});

			alarm.addAlarmAction(snsAction);
			alarm.addOkAction(snsAction);
		});

		adjustCloudformationParameters(this);

		new GuCname(this, 'DnsRecordForRegistration', {
			app,
			domainName: props.domainName,
			resourceRecord: loadBalancer.loadBalancerDnsName,
			ttl: Duration.hours(1),
		});
	}
}

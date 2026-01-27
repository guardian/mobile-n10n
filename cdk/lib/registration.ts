import { join } from 'path';
import { GuEc2App } from '@guardian/cdk';
import { AccessScope } from '@guardian/cdk/lib/constants';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuParameter, GuStack } from '@guardian/cdk/lib/constructs/core';
import { GuAllowPolicy } from '@guardian/cdk/lib/constructs/iam';
import { type App, Duration, Tags } from 'aws-cdk-lib';
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
import { CfnInclude } from 'aws-cdk-lib/cloudformation-include';
import { adjustCloudformationParameters } from './mobile-n10n-compatibility';

export interface RegistrationProps extends GuStackProps {
	app: string;
	instanceMetricGranularity: '1Minute' | '5Minute';
	minAsgSize: number;
	maxAsgSize?: number;
	low2xxIn30MinutesThreshold: number;
	low2xxIn24HoursThreshold: number;
}

export class Registration extends GuStack {
	constructor(scope: App, id: string, props: RegistrationProps) {
		super(scope, id, props);
		const yamlTemplateFilePath = join(
			__dirname,
			'../../registration/conf/registration.yaml',
		);
		// Until this project has been fully migrated to GuCDK you should update the 'old' infrastructure by modifying
		// the YAML file and then re-running the snapshot tests to confirm that the changes are being pulled through by
		// CDK
		new CfnInclude(this, 'YamlTemplate', {
			templateFile: yamlTemplateFilePath,
		});

		const {
			app,
			stage,
			stack,
			instanceMetricGranularity,
			minAsgSize,
			maxAsgSize,
			low2xxIn30MinutesThreshold,
			low2xxIn24HoursThreshold,
		} = props;

		const { account, region } = this;

		const { autoScalingGroup, loadBalancer } = new GuEc2App(this, {
			app,
			access: {
				scope: AccessScope.PUBLIC,
			},
			applicationPort: 9000,
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

		Tags.of(autoScalingGroup).add('gu:riffraff:new-asg', 'true');

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

		const low2xxIn30Minutes = loadBalancer.metrics
			.httpCodeTarget(HttpCodeTarget.TARGET_2XX_COUNT, {
				period: Duration.minutes(30),
				statistic: 'Sum',
			})
			.createAlarm(this, 'Low2XXIn30Minutes', {
				actionsEnabled: false,
				alarmDescription: `Triggers if load balancer in ${stage} does not have enough 200s in half an hour. ${runbookCopy}`,
				comparisonOperator: ComparisonOperator.LESS_THAN_THRESHOLD,
				evaluationPeriods: 1,
				threshold: low2xxIn30MinutesThreshold,
				treatMissingData: TreatMissingData.BREACHING,
			});

		low2xxIn30Minutes.addAlarmAction(snsAction);
		low2xxIn30Minutes.addOkAction(snsAction);

		const low2xxIn24Hours = loadBalancer.metrics
			.httpCodeTarget(HttpCodeTarget.TARGET_2XX_COUNT, {
				period: Duration.hours(24),
				statistic: 'Sum',
			})
			.createAlarm(this, 'Low2XXIn24Hours', {
				actionsEnabled: false,
				alarmDescription: `Triggers if load balancer in ${stage} does not have enough 200s in a whole day. ${runbookCopy}`,
				comparisonOperator: ComparisonOperator.LESS_THAN_THRESHOLD,
				evaluationPeriods: 1,
				threshold: low2xxIn24HoursThreshold,
				treatMissingData: TreatMissingData.BREACHING,
			});

		low2xxIn24Hours.addAlarmAction(snsAction);
		low2xxIn24Hours.addOkAction(snsAction);

		adjustCloudformationParameters(this);
	}
}

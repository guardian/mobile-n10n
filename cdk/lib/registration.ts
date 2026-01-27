import { join } from 'path';
import { GuEc2App } from '@guardian/cdk';
import { AccessScope } from '@guardian/cdk/lib/constants';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuParameter, GuStack } from '@guardian/cdk/lib/constructs/core';
import { GuAllowPolicy } from '@guardian/cdk/lib/constructs/iam';
import { type App, Tags } from 'aws-cdk-lib';
import {
	InstanceClass,
	InstanceSize,
	InstanceType,
	SecurityGroup,
} from 'aws-cdk-lib/aws-ec2';
import { CfnInclude } from 'aws-cdk-lib/cloudformation-include';
import { adjustCloudformationParameters } from './mobile-n10n-compatibility';

export interface RegistrationProps extends GuStackProps {
	app: string;
	instanceMetricGranularity: '1Minute' | '5Minute';
	minAsgSize: number;
	maxAsgSize?: number;
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
		} = props;

		const { account, region } = this;

		const { autoScalingGroup } = new GuEc2App(this, {
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

		adjustCloudformationParameters(this);
	}
}

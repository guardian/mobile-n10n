import { join } from 'path';
import { GuPlayApp } from '@guardian/cdk';
import { AccessScope } from '@guardian/cdk/lib/constants';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuVpcParameter } from '@guardian/cdk/lib/constructs/core';
import { GuLoggingStreamNameParameter } from '@guardian/cdk/lib/constructs/core';
import { GuDistributionBucketParameter } from '@guardian/cdk/lib/constructs/core';
import { GuStack } from '@guardian/cdk/lib/constructs/core';
import { GuAllowPolicy } from '@guardian/cdk/lib/constructs/iam';
import type { App } from 'aws-cdk-lib';
import { Tags } from 'aws-cdk-lib';
import { Duration } from 'aws-cdk-lib';
import type { CfnAutoScalingGroup } from 'aws-cdk-lib/aws-autoscaling';
import { InstanceClass, InstanceSize, InstanceType } from 'aws-cdk-lib/aws-ec2';
import { CfnInclude } from 'aws-cdk-lib/cloudformation-include';

export interface ReportProps extends GuStackProps {
	domainName:
		| 'report.notifications.guardianapis.com'
		| 'report.notifications.code.dev-guardianapis.com';
	instanceMetricGranularity: '1Minute' | '5Minute';
	loggingStreamParameterName:
		| '/account/services/logging.stream.name'
		| '/account/services/logging.stream.name.code';
	minAsgSize: number;
}

export class Report extends GuStack {
	constructor(scope: App, id: string, props: ReportProps) {
		super(scope, id, props);
		const yamlTemplateFilePath = join(
			__dirname,
			'../../report/conf/report.yaml',
		);
		new CfnInclude(this, 'YamlTemplate', {
			templateFile: yamlTemplateFilePath,
		});
		const app = 'report';
		const playApp = new GuPlayApp(this, {
			access: {
				scope: AccessScope.PUBLIC,
			},
			app,
			certificateProps: {
				domainName: props.domainName,
			},
			instanceMetricGranularity: props.instanceMetricGranularity,
			instanceType: InstanceType.of(InstanceClass.T4G, InstanceSize.MICRO),
			// This matches the YAML stack (i.e. there is no 5XX alarm).
			// There is no slo-alert config for this service either (presumably due to the traffic level), so we have an
			// observability gap here.
			monitoringConfiguration: { noMonitoring: true },
			roleConfiguration: {
				additionalPolicies: [
					new GuAllowPolicy(this, 'DynamoDbAccess', {
						// TODO tightly scope this (it allows for deletion 😱)
						actions: ['dynamodb:*'],
						resources: [
							`arn:aws:dynamodb:${this.region}:${this.account}:table/mobile-notifications-reports-${this.stage}`,
							`arn:aws:dynamodb:${this.region}:${this.account}:table/mobile-notifications-reports-${this.stage}/index/*`,
						],
					}),
					// The pattern provides parameter store access out of the box, but this service uses a non-standard path
					// so we also need this custom policy
					new GuAllowPolicy(this, 'CustomParameterStoreLocationAccess', {
						actions: ['ssm:GetParametersByPath'],
						resources: [
							`arn:aws:ssm:${this.region}:${this.account}:parameter/notifications/${this.stage}/${this.stack}`,
						],
					}),
				],
			},
			scaling: { minimumInstances: props.minAsgSize },
			userData: {
				distributable: {
					fileName: `${app}_1.0-latest_all.deb`,
					executionStatement: `dpkg -i /report/${app}_1.0-latest_all.deb`,
				},
			},
		});

		//TODO replace configure-aws-kinesis-agent with devx-logs?
		playApp.autoScalingGroup.userData.addCommands(
			`/opt/aws-kinesis-agent/configure-aws-kinesis-agent ${this.region} mobile-log-aggregation-${this.stage} /var/log/${app}/application.log`,
		);

		// This is necessary whilst dual-stacking because there is already a parameter called VpcId in the YAML template
		// Once the YAML template has been removed we should be able to drop this override
		GuVpcParameter.getInstance(this).overrideLogicalId('GuCdkVpcId');

		// This is needed to dual-stack; it can be removed once the legacy infrastructure is cleaned up
		Tags.of(playApp.autoScalingGroup).add('gu:riffraff:new-asg', 'true');

		//TODO check if this customisation is really necessary (copied across from YAML for now)
		const cfnAsg = playApp.autoScalingGroup.node
			.defaultChild as CfnAutoScalingGroup;
		cfnAsg.healthCheckGracePeriod = Duration.seconds(400).toSeconds();

		// In the Mobile account there are separate artifact buckets for different groups of applications, so we can't use
		// the account-wide default
		const distBucketParameterName = '/account/services/artifact.bucket.n10n';
		const distBucketParameter = GuDistributionBucketParameter.getInstance(this);
		distBucketParameter.allowedValues = [distBucketParameterName];
		distBucketParameter.default = distBucketParameterName;

		// In the Mobile account there are separate Kinesis streams for CODE and PROD, so we can't use the account-wide
		// default
		const loggingStreamParameter =
			GuLoggingStreamNameParameter.getInstance(this);
		loggingStreamParameter.allowedValues = [props.loggingStreamParameterName];
		loggingStreamParameter.default = props.loggingStreamParameterName;
	}
}

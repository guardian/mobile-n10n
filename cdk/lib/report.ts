import { GuPlayApp } from '@guardian/cdk';
import { AccessScope } from '@guardian/cdk/lib/constants';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuVpcParameter } from '@guardian/cdk/lib/constructs/core';
import { GuLoggingStreamNameParameter } from '@guardian/cdk/lib/constructs/core';
import { GuStack } from '@guardian/cdk/lib/constructs/core';
import { GuCname } from '@guardian/cdk/lib/constructs/dns';
import { GuAllowPolicy } from '@guardian/cdk/lib/constructs/iam';
import type { App } from 'aws-cdk-lib';
import { Duration } from 'aws-cdk-lib';
import type { CfnAutoScalingGroup } from 'aws-cdk-lib/aws-autoscaling';
import { InstanceClass, InstanceSize, InstanceType } from 'aws-cdk-lib/aws-ec2';
import { adjustCloudformationParameters } from './mobile-n10n-compatibility';

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

		const { stack, stage, region, account } = this;
		const { domainName, instanceMetricGranularity, minAsgSize } = props;

		const app = 'report';
		const { autoScalingGroup, loadBalancer } = new GuPlayApp(this, {
			access: {
				scope: AccessScope.PUBLIC,
			},
			app,
			certificateProps: {
				domainName,
			},
			instanceMetricGranularity,
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
							`arn:aws:dynamodb:${region}:${account}:table/mobile-notifications-reports-${stage}`,
							`arn:aws:dynamodb:${region}:${account}:table/mobile-notifications-reports-${stage}/index/*`,
						],
					}),
					// The pattern provides parameter store access out of the box, but this service uses a non-standard path
					// so we also need this custom policy
					new GuAllowPolicy(this, 'CustomParameterStoreLocationAccess', {
						actions: ['ssm:GetParametersByPath'],
						resources: [
							`arn:aws:ssm:${region}:${account}:parameter/notifications/${stage}/${stack}`,
						],
					}),
				],
			},
			scaling: { minimumInstances: minAsgSize },
			userData: {
				distributable: {
					fileName: `${app}_1.0-latest_all.deb`,
					executionStatement: `dpkg -i /report/${app}_1.0-latest_all.deb`,
				},
			},
			// Match existing healthcheck settings for now
			healthcheck: {
				healthyThresholdCount: 2,
				interval: Duration.seconds(30), // This seems unusually high - the default is 10s
				timeout: Duration.seconds(10), // The default is 5s
				unhealthyThresholdCount: 10, // This also seems unusually high - the default is 2
			},
		});

		//TODO check if this customisation is really necessary (it has been copied across from
		// the legacy infrastructure. The 30s healthcheck interval (see above) is probably part
		// of the problem here.
		const cfnAsg = autoScalingGroup.node.defaultChild as CfnAutoScalingGroup;
		cfnAsg.healthCheckGracePeriod = Duration.seconds(400).toSeconds();

		//TODO replace configure-aws-kinesis-agent with devx-logs?
		autoScalingGroup.userData.addCommands(
			`/opt/aws-kinesis-agent/configure-aws-kinesis-agent ${region} mobile-log-aggregation-${stage} /var/log/${app}/application.log`,
		);

		const vpcParameter = GuVpcParameter.getInstance(this);
		// This is necessary whilst dual-stacking because there is already a parameter called VpcId in the YAML template
		// Once the YAML template has been removed we should be able to drop this override
		vpcParameter.overrideLogicalId('GuCdkVpcId');

		adjustCloudformationParameters(this);

		// In the Mobile account there are separate Kinesis streams for CODE and PROD, so we can't use the account-wide
		// default
		const loggingStreamParameter =
			GuLoggingStreamNameParameter.getInstance(this);
		loggingStreamParameter.allowedValues = [props.loggingStreamParameterName];
		loggingStreamParameter.default = props.loggingStreamParameterName;

		new GuCname(this, 'DnsRecordForReport', {
			app,
			domainName,
			resourceRecord: loadBalancer.loadBalancerDnsName,
			ttl: Duration.seconds(60),
		});
	}
}

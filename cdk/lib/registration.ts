import { join } from 'path';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuStack } from '@guardian/cdk/lib/constructs/core';
import { GuErrorBudgetAlarmExperimental } from '@guardian/cdk/lib/experimental/constructs';
import type { App } from 'aws-cdk-lib';
import { MathExpression, Metric } from 'aws-cdk-lib/aws-cloudwatch';
import type { CfnLoadBalancer } from 'aws-cdk-lib/aws-elasticloadbalancing';
import { CfnInclude } from 'aws-cdk-lib/cloudformation-include';

export class Registration extends GuStack {
	constructor(scope: App, id: string, props: GuStackProps) {
		super(scope, id, props);
		const yamlTemplateFilePath = join(
			__dirname,
			'../../registration/conf/registration.yaml',
		);
		// Until this project has been fully migrated to GuCDK you should update the 'old' infrastructure by modifying
		// the YAML file and then re-running the snapshot tests to confirm that the changes are being pulled through by
		// CDK
		const cfnTemplate = new CfnInclude(this, 'YamlTemplate', {
			templateFile: yamlTemplateFilePath,
		});

		const registrationLoadBalancer = cfnTemplate.getResource(
			'LoadBalancerToPrivateASG',
		) as CfnLoadBalancer;

		const loadBalancerName = registrationLoadBalancer.ref;

		function loadBalancerMetric(metricName: string) {
			return new Metric({
				metricName,
				namespace: 'AWS/ELB',
				statistic: 'Sum',
				dimensionsMap: {
					LoadBalancerName: loadBalancerName,
				},
			});
		}

		const applicationServerErrors = loadBalancerMetric('HTTPCode_Backend_5XX');
		const loadBalancerErrors = loadBalancerMetric('HTTPCode_ELB_5XX');
		const allRequests = loadBalancerMetric('RequestCount');

		const budgetAlarm = new GuErrorBudgetAlarmExperimental(this, {
			sloName: `RegistrationAvailability${this.stage}Slo`,
			sloTarget: 0.999,
			badEvents: new MathExpression({
				expression: 'applicationServerErrors + loadBalancerErrors',
				usingMetrics: {
					applicationServerErrors,
					loadBalancerErrors,
				},
			}),
			validEvents: allRequests,
			snsTopicNameForAlerts: 'jacob-test',
		});
	}
}

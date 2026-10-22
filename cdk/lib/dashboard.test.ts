import { App } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { N10nServiceHealthDashboard } from './dashboard';

describe('The N10nServiceHealthDashboard stack', () => {
	it.each(['CODE', 'PROD'])(
		'matches the %s snapshot without adding alarms',
		(stage) => {
			const stack = new N10nServiceHealthDashboard(
				new App(),
				`N10nServiceHealthDashboard-${stage}`,
				{
					stack: 'mobile-notifications',
					stage,
					env: { region: 'eu-west-1' },
					workerSqsQueueName: `mobile-notifications-harvester-${stage}-Sqs-example`,
					dbInstanceId: `notifications-registrations-db-private-${stage.toLowerCase()}`,
				},
			);
			const template = Template.fromStack(stack);
			template.resourceCountIs('AWS::CloudWatch::Dashboard', 1);
			template.resourceCountIs('AWS::CloudWatch::Alarm', 0);
			const json = JSON.stringify(template.toJSON());
			expect(json).toContain(`NotificationLoadBalancerFullName-${stage}`);
			expect(json).toContain(`RegistrationLoadBalancerFullName-${stage}`);
			expect(template.toJSON()).toMatchSnapshot();
		},
	);
});

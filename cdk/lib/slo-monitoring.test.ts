import { App } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { SloMonitoring } from './slo-monitoring';

describe('The SloMonitoring stack', () => {
	it('matches the snapshot', () => {
		const app = new App();
		const stack = new SloMonitoring(app, 'SloMonitoring', {
			stack: 'mobile-notifications',
			stage: 'PROD',
		});
		const template = Template.fromStack(stack);
		expect(template.toJSON()).toMatchSnapshot();
	});
});

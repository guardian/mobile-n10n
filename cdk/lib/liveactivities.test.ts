import { App } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { liveActivitiesCodeProps } from '../bin/cdk';
import { LiveActivities } from './liveactivities';

describe('The LiveActivities stack', () => {
	it('matches the snapshot for CODE', () => {
		const app = new App();
		const stack = new LiveActivities(
			app,
			'LiveActivities-CODE',
			liveActivitiesCodeProps,
		);
		const template = Template.fromStack(stack);
		expect(template.toJSON()).toMatchSnapshot();
	});
});

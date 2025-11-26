import { App } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { SenderWorkerStack } from './senderworker';

describe('The Sender Worker stack', () => {
	it('matches the snapshot', () => {
		const app = new App();
		const stack = new SenderWorkerStack(app, 'SenderWorkerStack', {
			stack: 'mobile-notifications-workers',
			stage: 'PROD',
		});
		expect(Template.fromStack(stack).toJSON()).toMatchSnapshot();
	});
});

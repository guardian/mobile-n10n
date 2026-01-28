import { App } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { notificationCodeProps, notificationProdProps } from '../bin/cdk';
import { Notification } from './notification';

describe('The Notification stack', () => {
	it('matches the snapshot for CODE', () => {
		const app = new App();
		const stack = new Notification(
			app,
			'Notification-CODE',
			notificationCodeProps,
		);
		const template = Template.fromStack(stack);
		expect(template.toJSON()).toMatchSnapshot();
	});
	it('matches the snapshot for PROD', () => {
		const app = new App();
		const stack = new Notification(
			app,
			'Notification-PROD',
			notificationProdProps,
		);
		const template = Template.fromStack(stack);
		expect(template.toJSON()).toMatchSnapshot();
	});
});

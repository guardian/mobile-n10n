import { App } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import {
	footballNotificationsCodeProps,
	footballNotificationsProdProps,
} from '../bin/cdk';
import { FootballNotificationsLambda } from './footballnotificationslambda';

describe('The FootballNotificationsLambda stack', () => {
	it('matches the snapshot for CODE', () => {
		const app = new App();
		const stack = new FootballNotificationsLambda(
			app,
			'FootballNotificationsLambda-CODE',
			footballNotificationsCodeProps,
		);
		const template = Template.fromStack(stack);
		expect(template.toJSON()).toMatchSnapshot();
	});
	it('matches the snapshot for PROD', () => {
		const app = new App();
		const stack = new FootballNotificationsLambda(
			app,
			'FootballNotificationsLambda-PROD',
			footballNotificationsProdProps,
		);
		const template = Template.fromStack(stack);
		expect(template.toJSON()).toMatchSnapshot();
	});
});

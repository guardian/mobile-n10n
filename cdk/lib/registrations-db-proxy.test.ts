import { App } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { RegistrationsDbProxy } from './registrations-db-proxy';
import { dbProxyCodeProps, dbProxyProdProps } from '../bin/cdk';

describe('The RegistrationDbProxy stack', () => {
	it('matches the snapshot on CODE', () => {
		const app = new App();
		const stack = new RegistrationsDbProxy(app, 'RegistrationDbProxy-CODE', dbProxyCodeProps);
		const template = Template.fromStack(stack);
		expect(template.toJSON()).toMatchSnapshot();
	});
	it('matches the snapshot on PROD', () => {
		const app = new App();
		const stack = new RegistrationsDbProxy(app, 'RegistrationDbProxy-PROD', dbProxyProdProps);
		const template = Template.fromStack(stack);
		expect(template.toJSON()).toMatchSnapshot();
	});
});

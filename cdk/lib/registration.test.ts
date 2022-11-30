import { App } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { registrationCodeProps, registrationProdProps } from '../bin/cdk';
import { Registration } from './registration';

describe('The Registration stack', () => {
	it('matches the snapshot for CODE', () => {
		const app = new App();
		const stack = new Registration(
			app,
			'Registration-CODE',
			registrationCodeProps,
		);
		const template = Template.fromStack(stack);
		expect(template.toJSON()).toMatchSnapshot();
	});
	it('matches the snapshot for PROD', () => {
		const app = new App();
		const stack = new Registration(
			app,
			'Registration-PROD',
			registrationProdProps,
		);
		const template = Template.fromStack(stack);
		expect(template.toJSON()).toMatchSnapshot();
	});
});

import { App } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { reportPropsCode, reportPropsProd } from '../bin/cdk';
import { Report } from './report';

describe('The Report stack', () => {
	it('matches the snapshot for CODE', () => {
		const app = new App();
		const stack = new Report(app, 'Report-CODE', reportPropsCode);
		const template = Template.fromStack(stack);
		expect(template.toJSON()).toMatchSnapshot();
	});
	it('matches the snapshot for PROD', () => {
		const app = new App();
		const stack = new Report(app, 'Report-PROD', reportPropsProd);
		const template = Template.fromStack(stack);
		expect(template.toJSON()).toMatchSnapshot();
	});
});

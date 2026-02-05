import { App } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import {
	fakeBreakingNewsCodeProps,
	fakeBreakingNewsProdProps,
} from '../bin/cdk';
import { FakeBreakingNewsLambda } from './fakebreakingnewslambda';

describe('The FakeBreakingNewsLambda stack', () => {
	it('matches the snapshot for CODE', () => {
		const app = new App();
		const stack = new FakeBreakingNewsLambda(
			app,
			'FakeBreakingNewsLambda-CODE',
			fakeBreakingNewsCodeProps,
		);
		const template = Template.fromStack(stack);
		expect(template.toJSON()).toMatchSnapshot();
	});
	it('matches the snapshot for PROD', () => {
		const app = new App();
		const stack = new FakeBreakingNewsLambda(
			app,
			'FakeBreakingNewsLambda-PROD',
			fakeBreakingNewsProdProps,
		);
		const template = Template.fromStack(stack);
		expect(template.toJSON()).toMatchSnapshot();
	});
});

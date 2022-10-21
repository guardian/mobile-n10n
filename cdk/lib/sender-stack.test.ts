import { App } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { senderCodeProps, senderProdProps } from '../bin/cdk';
import { SenderWorkerStack } from './sender-stack';

describe('The SenderWorker stack', () => {
	it('matches the snapshot on CODE', () => {
		const app = new App();
		const stack = new SenderWorkerStack(
			app,
			'SenderWorkerStack-CODE',
			senderCodeProps,
		);
		const template = Template.fromStack(stack);
		expect(template.toJSON()).toMatchSnapshot();
	});
	it('matches the snapshot on PROD', () => {
		const app = new App();
		const stack = new SenderWorkerStack(
			app,
			'SenderWorkerStack-PROD',
			senderProdProps,
		);
		const template = Template.fromStack(stack);
		expect(template.toJSON()).toMatchSnapshot();
	});
});

import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuStack } from '@guardian/cdk/lib/constructs/core';
import { GuLambdaFunction } from '@guardian/cdk/lib/constructs/lambda';
import type { App } from 'aws-cdk-lib';
import { PolicyStatement } from 'aws-cdk-lib/aws-iam';
import { Runtime } from 'aws-cdk-lib/aws-lambda';
import { SqsEventSource } from 'aws-cdk-lib/aws-lambda-event-sources';
import { Queue } from 'aws-cdk-lib/aws-sqs';
import {
	ParameterDataType,
	ParameterTier,
	StringParameter,
} from 'aws-cdk-lib/aws-ssm';

export class SloMonitoring extends GuStack {
	constructor(scope: App, id: string, props: GuStackProps) {
		super(scope, id, props);

		const queue = new Queue(this, 'MessageQueue', {
			queueName: `notifications-slo-monitoring-${props.stage}`,
		});

		// this advertises the url of the queue to the notifications app
		new StringParameter(this, 'SenderQueueSSMParameter', {
			parameterName: `/notifications/${props.stage}/mobile-notifications/notifications.queues.sloMonitoring`,
			description:
				'Queue for SLO Monitoring service. N.B. this parameter is created via cdk',
			simpleName: false,
			stringValue: queue.queueUrl,
			tier: ParameterTier.STANDARD,
			dataType: ParameterDataType.TEXT,
		});

		// this advertises the arn of the queue which can be used by the notifications app cloudformation
		new StringParameter(this, 'SenderQueueSSMArnParameter', {
			parameterName: `/notifications/${props.stage}/mobile-notifications/notifications.queues.sloMonitoringArn`,
			description:
				'Arn for the queue for SLO Monitoring service. N.B. this parameter is created via cdk',
			simpleName: false,
			stringValue: queue.queueArn,
			tier: ParameterTier.STANDARD,
			dataType: ParameterDataType.TEXT,
		});

		const sloMonitor = new GuLambdaFunction(this, 'SloMonitor', {
			functionName: `mobile-notifications-slo-monitor-${props.stage}`,
			fileName: 'slomonitor.jar',
			handler: 'com.gu.notifications.slos.SloMonitor::handleMessage',
			runtime: Runtime.JAVA_11,
			app: 'slomonitor',
		});

		sloMonitor.addToRolePolicy(
			new PolicyStatement({
				actions: ['cloudwatch:PutMetricData'],
				resources: ['*'],
			}),
		);

		queue.grantConsumeMessages(sloMonitor);

		sloMonitor.addEventSource(new SqsEventSource(queue, { batchSize: 1 }));
	}
}

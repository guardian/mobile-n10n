import 'source-map-support/register';
import { App } from 'aws-cdk-lib';
import { InstanceClass, InstanceSize, InstanceType } from 'aws-cdk-lib/aws-ec2';
import { RegistrationsDbProxy } from '../lib/registrations-db-proxy';
import { SenderWorkerStack } from '../lib/sender-stack';
import { SloMonitoring } from '../lib/slo-monitoring';

const app = new App();

export const dbProxyCodeProps = {
	stack: 'mobile-notifications',
	stage: 'CODE',
	appName: 'registrations-db-proxy',
	dbHost:
		'notifications-registrations-db-private-code.crwidilr2ofx.eu-west-1.rds.amazonaws.com',
	dbName: 'registrationsCODE',
	dbInstanceId: 'notifications-registrations-db-private-code',
	dbSecurityGroupId: 'sg-0a228b61bbf4a0e6a',
};
export const dbProxyProdProps = {
	stack: 'mobile-notifications',
	stage: 'PROD',
	appName: 'registrations-db-proxy',
	dbHost:
		'notifications-registrations-db-private-prod.crwidilr2ofx.eu-west-1.rds.amazonaws.com',
	dbName: 'registrationsPROD',
	dbInstanceId: 'notifications-registrations-db-private-prod',
	dbSecurityGroupId: 'sg-01ee0bddb42ead7e5',
};

new RegistrationsDbProxy(app, 'RegistrationsDbProxy-CODE', dbProxyCodeProps);
new RegistrationsDbProxy(app, 'RegistrationsDbProxy-PROD', dbProxyProdProps);

new SloMonitoring(app, 'SloMonitor-CODE', {
	stack: 'mobile-notifications',
	stage: 'CODE',
});

new SloMonitoring(app, 'SloMonitor-PROD', {
	stack: 'mobile-notifications',
	stage: 'PROD',
});

export const senderCodeProps = {
	appName: 'sender-worker',
	stack: 'mobile-notifications-workers',
	stage: 'CODE',
	asgCapacity: {
		minimumInstances: 1,
		maximumInstances: 2,
	},
	instanceType: InstanceType.of(InstanceClass.T4G, InstanceSize.SMALL),
	targetCpuUtilization: 20,
	notificationSnsTopic:
		'arn:aws:sns:eu-west-1:201359054765:AutoscalingNotificationsCODE',
	alarmSnsTopic: 'mobile-server-side',
	alarmEnabled: false,
	cleanerQueueArn:
		'arn:aws:sqs:eu-west-1:201359054765:mobile-notifications-registration-cleaning-worker-CODE-Sqs-1CFISZQCN49SR',
};
new SenderWorkerStack(app, 'SenderWorker-CODE', senderCodeProps);

export const senderProdProps = {
	appName: 'sender-worker',
	stack: 'mobile-notifications-workers',
	stage: 'PROD',
	asgCapacity: {
		minimumInstances: 1,
		maximumInstances: 2,
	},
	instanceType: InstanceType.of(InstanceClass.T4G, InstanceSize.SMALL),
	targetCpuUtilization: 20,
	notificationSnsTopic:
		'arn:aws:sns:eu-west-1:201359054765:AutoscalingNotificationsPROD',
	alarmSnsTopic: 'mobile-server-side',
	alarmEnabled: true,
	cleanerQueueArn:
		'arn:aws:sqs:eu-west-1:201359054765:mobile-notifications-registration-cleaning-worker-PROD-Sqs-12LNONCNWBRWK',
};
new SenderWorkerStack(app, 'SenderWorker-PROD', senderProdProps);

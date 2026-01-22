import 'source-map-support/register';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { App } from 'aws-cdk-lib';
import { Notification, type NotificationProps } from '../lib/notification';
import { Registration } from '../lib/registration';
import { RegistrationsDbProxy } from '../lib/registrations-db-proxy';
import { Report, type ReportProps } from '../lib/report';
import { SenderWorkerStack } from '../lib/senderworker';
import { SloMonitoring } from '../lib/slo-monitoring';

const app = new App();

export const notificationCodeProps: NotificationProps = {
	stack: 'mobile-notifications',
	stage: 'CODE',
	env: { region: 'eu-west-1' },
	domainName: 'notification.notifications.code.dev-guardianapis.com',
	instanceMetricGranularity: '5Minute',
	workerSqsQueueArn:
		'arn:aws:sqs:eu-west-1:201359054765:mobile-notifications-harvester-CODE-Sqs-1R9TBA4F2C6TG',
	s3TopicCountBucket: 'mobile-notifications-topics',
	alarmTopicArn: 'arn:aws:sns:eu-west-1:201359054765:mobile-server-side',
	minAsgSize: 1,
	maxAsgSize: 2,
	dailyNewsstandPushCount: 0,
};

export const notificationProdProps: NotificationProps = {
	stack: 'mobile-notifications',
	stage: 'PROD',
	env: { region: 'eu-west-1' },
	domainName: 'notification.notifications.guardianapis.com',
	instanceMetricGranularity: '1Minute',
	workerSqsQueueArn:
		'arn:aws:sqs:eu-west-1:201359054765:mobile-notifications-harvester-PROD-Sqs-NPP9X15G7WAO',
	s3TopicCountBucket: 'mobile-notifications-topics',
	alarmTopicArn: 'arn:aws:sns:eu-west-1:201359054765:mobile-server-side',
	minAsgSize: 3,
	maxAsgSize: 6,
	dailyNewsstandPushCount: 1,
};

new Notification(app, 'Notification-CODE', notificationCodeProps);
new Notification(app, 'Notification-PROD', notificationProdProps);

export const registrationCodeProps: GuStackProps = {
	stack: 'mobile-notifications',
	stage: 'CODE',
};

export const registrationProdProps: GuStackProps = {
	stack: 'mobile-notifications',
	stage: 'PROD',
};

new Registration(app, 'Registration-CODE', registrationCodeProps);
new Registration(app, 'Registration-PROD', registrationProdProps);

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

new SenderWorkerStack(app, 'SenderWorkerStack-CODE', {
	stack: 'mobile-notifications-workers',
	stage: 'CODE',
	cloudFormationStackName: 'notification-sender-workers-cdk-CODE',
});

new SenderWorkerStack(app, 'SenderWorkerStack-PROD', {
	stack: 'mobile-notifications-workers',
	stage: 'PROD',
});

export const reportPropsCode: ReportProps = {
	cloudFormationStackName: 'mobile-notifications-report-CODE',
	env: { region: 'eu-west-1' },
	stack: 'mobile-notifications',
	stage: 'CODE',
	app: 'report',
	domainName: 'report.notifications.code.dev-guardianapis.com',
	instanceMetricGranularity: '5Minute',
	minAsgSize: 1,
};
new Report(app, 'Report-CODE', reportPropsCode);

export const reportPropsProd: ReportProps = {
	cloudFormationStackName: 'mobile-notifications-report-PROD',
	env: { region: 'eu-west-1' },
	stack: 'mobile-notifications',
	stage: 'PROD',
	app: 'report',
	domainName: 'report.notifications.guardianapis.com',
	instanceMetricGranularity: '1Minute',
	minAsgSize: 3,
};
new Report(app, 'Report-PROD', reportPropsProd);

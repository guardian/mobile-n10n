import 'source-map-support/register';
import { App, Duration } from 'aws-cdk-lib';
import type { NotificationProps } from '../lib/notification';
import { Notification } from '../lib/notification';
import type { RegistrationProps } from '../lib/registration';
import { Registration } from '../lib/registration';
import { RegistrationsDbProxy } from '../lib/registrations-db-proxy';
import { Report, type ReportProps } from '../lib/report';
import { SenderWorkerStack } from '../lib/senderworker';
import { SloMonitoring } from '../lib/slo-monitoring';

const app = new App();

export const notificationCodeProps: NotificationProps = {
	stack: 'mobile-notifications',
	stage: 'CODE',
};

export const notificationProdProps: NotificationProps = {
	stack: 'mobile-notifications',
	stage: 'PROD',
};

new Notification(app, 'Notification-CODE', notificationCodeProps);
new Notification(app, 'Notification-PROD', notificationProdProps);

export const registrationCodeProps: RegistrationProps = {
	stack: 'mobile-notifications',
	stage: 'CODE',
	app: 'registration',
	env: { region: 'eu-west-1' },
	domainName: 'notifications.code.dev-guardianapis.com',
	instanceMetricGranularity: '5Minute',
	minAsgSize: 1,
	low2xxAlarms: [
		{ period: Duration.minutes(30), threshold: 0 },
		{ period: Duration.hours(24), threshold: 1 },
	],
	cloudFormationStackName: 'mobile-notifications-registration-CODE',
};

export const registrationProdProps: RegistrationProps = {
	stack: 'mobile-notifications',
	stage: 'PROD',
	app: 'registration',
	env: { region: 'eu-west-1' },
	domainName: 'notifications.guardianapis.com',
	instanceMetricGranularity: '1Minute',
	maxAsgSize: 12,
	minAsgSize: 3,
	low2xxAlarms: [
		{ period: Duration.minutes(30), threshold: 1_000 },
		{ period: Duration.hours(24), threshold: 150_000 },
	],
	cloudFormationStackName: 'mobile-notifications-registration-PROD',
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
	buildIdentifier: process.env.BUILD_NUMBER ?? 'DEV',
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
	buildIdentifier: process.env.BUILD_NUMBER ?? 'DEV',
};
new Report(app, 'Report-PROD', reportPropsProd);

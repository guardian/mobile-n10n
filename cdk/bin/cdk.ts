import 'source-map-support/register';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { App } from 'aws-cdk-lib';
import { Registration } from '../lib/registration';
import { RegistrationsDbProxy } from '../lib/registrations-db-proxy';
import { SloMonitoring } from '../lib/slo-monitoring';

const app = new App();

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

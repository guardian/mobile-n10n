import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { AppIdentity, GuStack } from '@guardian/cdk/lib/constructs/core';
import { GuVpc, SubnetType } from '@guardian/cdk/lib/constructs/ec2';
import type { App } from 'aws-cdk-lib';
import { Arn, ArnFormat, CfnOutput } from 'aws-cdk-lib';
import { SecurityGroup } from 'aws-cdk-lib/aws-ec2';
import {
	DatabaseInstance,
	DatabaseInstanceEngine,
	DatabaseProxy,
	ProxyTarget,
} from 'aws-cdk-lib/aws-rds';
import { Secret } from 'aws-cdk-lib/aws-secretsmanager';
import { adjustCloudformationParameters } from './mobile-n10n-compatibility';

interface DbProxyStackProps extends GuStackProps {
	appName: string;
	dbHost: string;
	dbName: string;
	dbInstanceId: string;
	dbSecurityGroupId: string;
}

export class RegistrationsDbProxy extends GuStack {
	constructor(scope: App, id: string, props: DbProxyStackProps) {
		super(scope, id, props);

		const vpc = GuVpc.fromIdParameter(
			this,
			AppIdentity.suffixText({ app: props.appName }, 'VPC'),
		);

		const dbPort = 5432;
		const secretTemplate = {
			engine: 'postgres',
			host: props.dbHost,
			port: dbPort,
			dbname: props.dbName,
			dbInstanceIdentifier: props.dbInstanceId,
		};
		const dbWorkerSecret = new Secret(this, 'RegistrationDbWorkerSecret', {
			secretName: `registrations-db-worker-secret-${props.stage}`,
			description:
				'Secrets for accessing registration database from worker lambdas',
			generateSecretString: {
				secretStringTemplate: JSON.stringify({
					username: 'worker_user',
					...secretTemplate,
				}),
				generateStringKey: 'password',
			},
		});
		const dbCleanerSecret = new Secret(this, 'RegistrationDbCleanerSecret', {
			secretName: `registrations-db-cleaner-secret-${props.stage}`,
			description:
				'Secrets for accessing registration database from cleaner lambdas',
			generateSecretString: {
				secretStringTemplate: JSON.stringify({
					username: 'cleaner_user',
					...secretTemplate,
				}),
				generateStringKey: 'password',
			},
		});

		const dbSecurityGroup = SecurityGroup.fromSecurityGroupId(
			this,
			'registrations-db-security-group',
			props.dbSecurityGroupId,
		);
		const registrationDb = DatabaseInstance.fromDatabaseInstanceAttributes(
			this,
			'registrations-db',
			{
				instanceEndpointAddress: props.dbHost,
				instanceIdentifier: props.dbInstanceId,
				port: dbPort,
				securityGroups: [dbSecurityGroup],
				engine: DatabaseInstanceEngine.POSTGRES,
			},
		);

		const proxy = new DatabaseProxy(this, 'RegistrationsDbProxy', {
			dbProxyName: `registrations-db-proxy-cdk-${props.stage}`,
			proxyTarget: ProxyTarget.fromInstance(registrationDb),
			secrets: [dbWorkerSecret, dbCleanerSecret],
			vpc,
			iamAuth: false,
			maxConnectionsPercent: 90,
			securityGroups: [dbSecurityGroup],
			requireTLS: false,
			vpcSubnets: {
				subnets: GuVpc.subnetsFromParameter(this, {
					type: SubnetType.PRIVATE,
					app: props.appName,
				}),
			},
		});

		const proxyResourceId = Arn.split(
			proxy.dbProxyArn,
			ArnFormat.COLON_RESOURCE_NAME,
		).resourceName;
		if (proxyResourceId != undefined) {
			const grantId = `arn:aws:rds-db:${this.region}:${this.account}:dbuser:${proxyResourceId}/*`;
			new CfnOutput(this, 'RegistrationsDbProxyId', {
				value: grantId,
				description: 'ID of RDS proxy to registrations database',
				exportName: `RegistrationsDbProxyId-${props.stage}`,
			});
		}

		adjustCloudformationParameters(this);
	}
}

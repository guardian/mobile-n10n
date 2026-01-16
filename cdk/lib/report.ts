import { GuPlayApp, GuScheduledLambda } from '@guardian/cdk';
import { AccessScope } from '@guardian/cdk/lib/constants';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuVpcParameter } from '@guardian/cdk/lib/constructs/core';
import { GuLoggingStreamNameParameter } from '@guardian/cdk/lib/constructs/core';
import { GuStack } from '@guardian/cdk/lib/constructs/core';
import { GuCname } from '@guardian/cdk/lib/constructs/dns';
import { GuAllowPolicy } from '@guardian/cdk/lib/constructs/iam';
import type { App } from 'aws-cdk-lib';
import { Tags } from 'aws-cdk-lib';
import { CfnParameter } from 'aws-cdk-lib';
import { Duration, RemovalPolicy } from 'aws-cdk-lib';
import type { CfnAutoScalingGroup } from 'aws-cdk-lib/aws-autoscaling';
import { Table } from 'aws-cdk-lib/aws-dynamodb';
import { InstanceClass, InstanceSize, InstanceType } from 'aws-cdk-lib/aws-ec2';
import type { CfnRule } from 'aws-cdk-lib/aws-events';
import { Schedule } from 'aws-cdk-lib/aws-events';
import { Effect, PolicyStatement } from 'aws-cdk-lib/aws-iam';
import { LoggingFormat, Runtime } from 'aws-cdk-lib/aws-lambda';
import { Bucket } from 'aws-cdk-lib/aws-s3';
import { adjustCloudformationParameters } from './mobile-n10n-compatibility';

export interface ReportProps extends GuStackProps {
	domainName:
		| 'report.notifications.guardianapis.com'
		| 'report.notifications.code.dev-guardianapis.com';
	instanceMetricGranularity: '1Minute' | '5Minute';
	loggingStreamParameterName:
		| '/account/services/logging.stream.name'
		| '/account/services/logging.stream.name.code';
	minAsgSize: number;
}

export class Report extends GuStack {
	constructor(scope: App, id: string, props: ReportProps) {
		super(scope, id, props);

		// Tag all resources with the current build and deployment ID.
		// Useful for debugging as the tags should show in Central ELK too.
		['BuildId', 'RiffRaffDeploymentId'].forEach((metadata) => {
			const param = new CfnParameter(this, metadata, {
				type: 'String',
				description: 'For Riff-Raff to fill in at deploy time',
			});
			Tags.of(this).add(metadata, param.valueAsString);
		});

		const { stack, stage, region, account } = this;
		const { domainName, instanceMetricGranularity, minAsgSize } = props;

		const dynamoTable = Table.fromTableName(
			this,
			'ReportsTable',
			`mobile-notifications-reports-${stage}`,
		);

		const app = 'report';
		const { autoScalingGroup, loadBalancer } = new GuPlayApp(this, {
			access: {
				scope: AccessScope.PUBLIC,
			},
			app,
			certificateProps: {
				domainName,
			},
			instanceMetricGranularity,
			instanceType: InstanceType.of(InstanceClass.T4G, InstanceSize.MICRO),
			// This matches the YAML stack (i.e. there is no 5XX alarm).
			// There is no slo-alert config for this service either (presumably due to the traffic level), so we have an
			// observability gap here.
			monitoringConfiguration: { noMonitoring: true },
			roleConfiguration: {
				additionalPolicies: [
					new GuAllowPolicy(this, 'DynamoDbAccess', {
						// TODO tightly scope this (it allows for deletion 😱)
						actions: ['dynamodb:*'],
						resources: [
							dynamoTable.tableArn,
							`${dynamoTable.tableArn}/index/*`,
						],
					}),
					// The pattern provides parameter store access out of the box, but this service uses a non-standard path
					// so we also need this custom policy
					new GuAllowPolicy(this, 'CustomParameterStoreLocationAccess', {
						actions: ['ssm:GetParametersByPath'],
						resources: [
							`arn:aws:ssm:${region}:${account}:parameter/notifications/${stage}/${stack}`,
						],
					}),
				],
			},
			scaling: { minimumInstances: minAsgSize },
			userData: {
				distributable: {
					fileName: `${app}_1.0-latest_all.deb`,
					executionStatement: `dpkg -i /report/${app}_1.0-latest_all.deb`,
				},
			},
			// Match existing healthcheck settings for now
			healthcheck: {
				healthyThresholdCount: 2,
				interval: Duration.seconds(30), // This seems unusually high - the default is 10s
				timeout: Duration.seconds(10), // The default is 5s
				unhealthyThresholdCount: 10, // This also seems unusually high - the default is 2
			},
		});

		//TODO check if this customisation is really necessary (it has been copied across from
		// the legacy infrastructure. The 30s healthcheck interval (see above) is probably part
		// of the problem here.
		const cfnAsg = autoScalingGroup.node.defaultChild as CfnAutoScalingGroup;
		cfnAsg.healthCheckGracePeriod = Duration.seconds(400).toSeconds();

		//TODO replace configure-aws-kinesis-agent with devx-logs?
		autoScalingGroup.userData.addCommands(
			`/opt/aws-kinesis-agent/configure-aws-kinesis-agent ${region} mobile-log-aggregation-${stage} /var/log/${app}/application.log`,
		);

		const vpcParameter = GuVpcParameter.getInstance(this);
		// This is necessary whilst dual-stacking because there is already a parameter called VpcId in the YAML template
		// Once the YAML template has been removed we should be able to drop this override
		vpcParameter.overrideLogicalId('GuCdkVpcId');

		adjustCloudformationParameters(this);

		// In the Mobile account there are separate Kinesis streams for CODE and PROD, so we can't use the account-wide
		// default
		const loggingStreamParameter =
			GuLoggingStreamNameParameter.getInstance(this);
		loggingStreamParameter.allowedValues = [props.loggingStreamParameterName];
		loggingStreamParameter.default = props.loggingStreamParameterName;

		new GuCname(this, 'DnsRecordForReport', {
			app,
			domainName,
			resourceRecord: loadBalancer.loadBalancerDnsName,
			ttl: Duration.seconds(60),
		});

		const reportExtractorApp = 'reportextractor';
		const reportExtractor = new GuScheduledLambda(this, 'ReportExtractor', {
			description: 'Export sent notifications to the datalake',
			app: reportExtractorApp,
			fileName: `${reportExtractorApp}.jar`,
			handler: 'com.gu.notifications.extractor.Lambda',
			rules: [
				{
					// Run at 01:00 AM (UTC) every day.
					schedule: Schedule.expression('cron(0 1 1/1 * ? *)'),
				},
			],
			runtime: Runtime.JAVA_11,
			memorySize: 1024,
			reservedConcurrentExecutions: 1,
			timeout: Duration.minutes(1),
			monitoringConfiguration: { noMonitoring: true },
			loggingFormat: LoggingFormat.TEXT,

			// Although GuCDK provides UPPERCASED env vars for stack, stage, app, simple-configuration reads Title cased env vars.
			environment: {
				Stack: stack,
				Stage: stage,
				App: reportExtractorApp,
			},

			functionName: [stack, reportExtractorApp, stage].join('-'),
		});

		reportExtractor.addToRolePolicy(
			new PolicyStatement({
				effect: Effect.ALLOW,
				actions: ['dynamodb:GetItem', 'dynamodb:Query'],
				resources: [
					dynamoTable.tableArn,
					`${dynamoTable.tableArn}/index/sentTime-index`,
				],
			}),
		);

		// TODO push this bucket name to SSM Parameters and remove hardcoding it in the lambda source code (`extractor/Lambda.scala`).
		const reportExtractorOutputBucket = Bucket.fromBucketArn(
			this,
			'ReportExtractorOutputBucket',
			// This bucket lives in the Ophan account.
			'arn:aws:s3:::ophan-raw-push-notification',
		);

		reportExtractor.addToRolePolicy(
			new PolicyStatement({
				effect: Effect.ALLOW,
				actions: ['s3:PutObject', 's3:PutObjectAcl'],

				/*
				The lambda writes to the following path:
					(data|code-data)/date=/notifications.json
				TODO can we tighten the permissions here?
				 */
				resources: [
					reportExtractorOutputBucket.bucketArn,
					reportExtractorOutputBucket.arnForObjects('*'),
				],
			}),
		);

		const eventLogsBucket = new Bucket(this, 'EventConsumerBucket', {
			bucketName: `aws-mobile-event-logs-${stage.toLowerCase()}`,
			removalPolicy: RemovalPolicy.RETAIN,
			lifecycleRules: [{ expiration: Duration.days(21), enabled: true }],
		});

		const eventConsumerApp = 'eventconsumer';
		const eventConsumer = new GuScheduledLambda(this, 'EventConsumer', {
			description: 'Queries Athena to update Notification reports in Dynamodb',
			app: eventConsumerApp,
			fileName: `${eventConsumerApp}.jar`,
			handler: 'com.gu.notifications.events.AthenaLambda::handleRequest',
			rules: [
				{
					// Run every 5 minutes, starting at 2 minutes past the hour. (2, 7, 12 etc)
					schedule: Schedule.expression('cron(2/5 * * * ? *)'),
				},
			],
			runtime: Runtime.JAVA_11,
			memorySize: 1024,
			reservedConcurrentExecutions: 1,
			timeout: Duration.minutes(2),
			monitoringConfiguration: { noMonitoring: true },

			// Although GuCDK provides UPPERCASED env vars for stack, stage, app, simple-configuration reads Title cased env vars.
			environment: {
				Stack: stack,
				Stage: stage,
				App: eventConsumerApp,
				IngestLocation: `s3://${eventLogsBucket.bucketName}/fastly/notifications/received`,
				AthenaOutputLocation: `s3://${eventLogsBucket.bucketName}/athena`,
				AthenaDatabase: 'notifications',
			},
			functionName: [stack, eventConsumerApp, stage].join('-'),
		});

		// Disable the schedule rule for testing
		const eventConsumerRule = eventConsumer.node.findChild(
			'EventConsumer-cron(2/5 * * * ? *)-0',
		).node.defaultChild as CfnRule;
		eventConsumerRule.state = 'DISABLED';

		eventConsumer.addToRolePolicy(
			new PolicyStatement({
				effect: Effect.ALLOW,
				actions: [
					'athena:StartQueryExecution',
					'athena:GetQueryResultsStream',
					'athena:GetNamespace',
					'athena:GetQueryResults',
					'athena:GetQueryExecutions',
					'athena:GetNamedQuery',
					'athena:GetCatalogs',
					'athena:GetNamespaces',
					'athena:GetWorkGroup',
					'athena:GetExecutionEngine',
					'athena:GetQueryExecution',
					'athena:GetExecutionEngines',
					'athena:GetTables',
					'athena:GetTable',
					'athena:BatchGetNamedQuery',
					'athena:BatchGetQueryExecution',
					'glue:GetTable',
					'glue:GetPartition',
					'glue:GetPartitions',
					'glue:GetDatabase',
					'glue:BatchCreatePartition',
				],
				resources: ['*'],
			}),
		);

		eventConsumer.addToRolePolicy(
			new PolicyStatement({
				effect: Effect.ALLOW,
				actions: ['s3:*'],
				resources: [
					eventLogsBucket.bucketArn,
					eventLogsBucket.arnForObjects('*'),
				],
			}),
		);

		eventConsumer.addToRolePolicy(
			new PolicyStatement({
				effect: Effect.ALLOW,
				actions: ['dynamodb:*'],
				resources: [dynamoTable.tableArn],
			}),
		);
	}
}

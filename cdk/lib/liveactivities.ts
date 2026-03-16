import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuStack } from '@guardian/cdk/lib/constructs/core';
import type { App } from 'aws-cdk-lib';
import { Tags } from 'aws-cdk-lib';
import { AttributeType, BillingMode, Table } from 'aws-cdk-lib/aws-dynamodb';

export class LiveActivities extends GuStack {
	constructor(scope: App, id: string, props: GuStackProps) {
		super(scope, id, props);

		const { stack, stage } = this;
		const app = 'liveactivities';

		const dynamoTableName = `${stack}-liveactivities-${stage}`;

		const dynamoTable = new Table(this, `${app}-dynamo-table`, {
			tableName: dynamoTableName,
			partitionKey: { name: 'id', type: AttributeType.STRING },
			billingMode: BillingMode.PAY_PER_REQUEST,
		});
		Tags.of(dynamoTable).add('devx-backup-enabled', 'true');
	}
}

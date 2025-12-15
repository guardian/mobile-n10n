import { join } from 'path';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuStack } from '@guardian/cdk/lib/constructs/core';
import type { App } from 'aws-cdk-lib';
import { CfnInclude } from 'aws-cdk-lib/cloudformation-include';

// eslint-disable-next-line @typescript-eslint/no-empty-object-type -- WIP
export interface ReportProps extends GuStackProps {}

export class Report extends GuStack {
	constructor(scope: App, id: string, props: ReportProps) {
		super(scope, id, props);
		const yamlTemplateFilePath = join(
			__dirname,
			'../../report/conf/report.yaml',
		);
		new CfnInclude(this, 'YamlTemplate', {
			templateFile: yamlTemplateFilePath,
		});
	}
}

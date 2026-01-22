import { join } from 'path';
import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuStack } from '@guardian/cdk/lib/constructs/core';
import type { App } from 'aws-cdk-lib';
import { CfnInclude } from 'aws-cdk-lib/cloudformation-include';

export class Notification extends GuStack {
	constructor(scope: App, id: string, props: GuStackProps) {
		super(scope, id, props);
		const yamlTemplateFilePath = join(
			__dirname,
			'../../notification/conf/notification.yaml',
		);
		// Until this project has been fully migrated to GuCDK you should update the 'old' infrastructure by modifying
		// the YAML file and then re-running the snapshot tests to confirm that the changes are being pulled through by
		// CDK
		new CfnInclude(this, 'YamlTemplate', {
			templateFile: yamlTemplateFilePath,
		});
	}
}

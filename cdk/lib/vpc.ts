import type { GuStack } from '@guardian/cdk/lib/constructs/core';

/**
 * Adjusts VPC-related CloudFormation parameters in the given stack to use the `notifications` VPC.
 * @see // https://github.com/guardian/aws-account-setup/blob/67a516b65e2e151d69687fa61a8a1aa914e8b7c0/packages/cdk/lib/__snapshots__/aws-account-setup.test.ts.snap#L27280-L27327
 */
export function adjustVpcParameters(stack: GuStack) {
	const primaryVpcPrefix = '/account/vpc/primary/';
	const notificationVpcPrefix = '/account/vpc/notifications/';

	const parameters = Object.values(stack.parameters)
		.filter((_) => !!_.default)
		.filter((_) => {
			const defaultValue = _.default as string;
			return defaultValue.startsWith(primaryVpcPrefix);
		});

	parameters.forEach((parameter) => {
		parameter.default = (parameter.default as string).replace(
			primaryVpcPrefix,
			notificationVpcPrefix,
		);
		parameter.allowedValues = [parameter.default];
	});
}

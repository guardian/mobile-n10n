import type { GuStack } from '@guardian/cdk/lib/constructs/core';

/**
 * Adjusts CloudFormation parameters in a given stack (if they exist).
 *
 * @param stack - The stack to adjust.
 * @param currentDefaultValue - The current default value to look for. Can be a prefix, or a full value.
 * @param desiredDefaultValue - The desired default value to replace with. Can be a prefix, or a full value.
 */
function adjustParameter(
	stack: GuStack,
	currentDefaultValue: string,
	desiredDefaultValue: string,
) {
	const parameters = Object.values(stack.parameters)
		.filter((parameter) => !!parameter.default)
		.filter((parameter) => {
			const defaultValue = parameter.default as string;

			// Use `startsWith` to allow for partial matches (i.e. prefixes)
			return defaultValue.startsWith(currentDefaultValue);
		});

	parameters.forEach((parameter) => {
		parameter.default = (parameter.default as string).replace(
			currentDefaultValue,
			desiredDefaultValue,
		);
		parameter.allowedValues = [parameter.default];
	});
}

/**
 * Adjusts VPC-related CloudFormation parameters in the given stack to use the `notifications` VPC.
 * @see https://github.com/guardian/aws-account-setup/blob/67a516b65e2e151d69687fa61a8a1aa914e8b7c0/packages/cdk/lib/__snapshots__/aws-account-setup.test.ts.snap#L27280-L27327
 */
function adjustVpcParameters(stack: GuStack) {
	adjustParameter(
		stack,
		'/account/vpc/primary/',
		'/account/vpc/notifications/',
	);
}

/**
 * Adjusts the artifact bucket parameter for n10n services.
 * In the Mobile account there are separate artifact buckets for different groups of applications,
 * so we can't use the account-wide default.
 */
function adjustArtifactBucketParameter(stack: GuStack) {
	adjustParameter(
		stack,
		'/account/services/artifact.bucket',
		'/account/services/artifact.bucket.n10n',
	);
}

/**
 * Applications within the `mobile-n10n` stack do not use account (/GuCDK) defaults.
 * This adjusts CloudFormation parameters with values suitable for the `mobile-n10n` stack.
 */
export function adjustCloudformationParameters(stack: GuStack) {
	adjustVpcParameters(stack);
	adjustArtifactBucketParameter(stack);
}

import '@aws-cdk/assert/jest';
import { SynthUtils } from '@aws-cdk/assert';
import { App } from '@aws-cdk/core';
import {SenderWorkerStack} from "./senderworker";

describe('The MobileAppsRendering stack', () => {
  it('matches the snapshot', () => {
    const app = new App();
    const stack = new SenderWorkerStack(app, 'SenderWorkerStack', {
      stack: 'mobile-notifications-workers'
    });
    expect(SynthUtils.toCloudFormation(stack)).toMatchSnapshot();
  });
});

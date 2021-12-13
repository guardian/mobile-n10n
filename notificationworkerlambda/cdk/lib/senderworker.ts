import type { App } from "@aws-cdk/core"
import { GuStack } from "@guardian/cdk/lib/constructs/core"
//import type { GuStackProps } from "@guardian/cdk/lib/constructs/core"

export class SenderWorkerStack extends GuStack {
  constructor(scope: App, id: string/*, props: GuStackProps*/) {
    let props = { stack: "foo" }
    super(scope, id, props)
  }
}

// // import * as cdk from '@aws-cdk/core';
// import { GuStack } from "@guardian/cdk/lib/constructs/core";
// import type { App } from "@aws-cdk/core";
// import type { GuStackProps } from "@guardian/cdk/lib/constructs/core";

// export class SenderWorkerStack extends GuStack {
//   // constructor(scope: App, id: string, props: GuStackProps) {
//   //   super(scope, id, props);
//   // }
//     // The code that defines your stack goes here

//     // example resource
//     // const queue = new sqs.Queue(this, 'CdkQueue', {
//     //   visibilityTimeout: cdk.Duration.seconds(300)
//     // });
// }

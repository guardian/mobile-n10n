# Infrastructure

This directory defines the components to be deployed to AWS.

See [`package.json`](./package.json) for a list of available scripts.

## Notification Lambda CDK

This directory contains the CDK for the Cloudformation that describes the notification lambda architecture.

The `cdk.json` file tells the CDK Toolkit how to execute your app.

### Scope

Currently this defines the notification sender lambdas because there are 5 of
these with almost identical configurations.

### Useful commands

* `npm run build`   compile typescript to js
* `npm run watch`   watch for changes and compile
* `npm run test`    perform the jest unit tests
* `cdk deploy`      deploy this stack to your default AWS account/region
* `cdk diff`        compare deployed stack with current state
* `cdk synth`       emits the synthesized CloudFormation template

##- Stack dependencies

It's important to note that there is an [Exported
Value](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-stack-exports.html)
which is exported from this stack and consumed by the Harvester stack. This is
because Harvester needs to know the ARNs of the SQS queues that are created here
for each notification sender.

The off-shoot of this is that AWS will prevent you from making a change to this
list of ARNs while it is being consumed by the other stack.

Note that this is only a problem *in the very rare case* of when you are making
a change which actually affects this list of ARNs &mdash; e.g. adding a new
lambda or removing an old one &mdash; any other changes should be fine.

If you are making such a change, the deployment will fail (harmlessly, as it
will just be rolled back).

#### Deploying if you change the list of SQS queues

In that case, what you should do is, copy the currently exported value (either
via the console or using the following snippet, replacing `CODE` with the
correct stage of course):

``` shell
$ aws cloudformation list-exports | jq -r '.Exports | map(select(.Name == "NotificationSenderWorkerQueueArns-CODE")) | .[0].Value'
```

and then insert this into the harvester cloudformation as a literal value
instead of the import. The simplest way to do this is just to edit the YAML file
and upload it. The diff would look something like this:

``` shell
diff --git a/notificationworkerlambda/harvester-cfn.yaml b/notificationworkerlambda/harvester-cfn.yaml
index 3e4fcbf1..e83c63fa 100644
--- a/notificationworkerlambda/harvester-cfn.yaml
+++ b/notificationworkerlambda/harvester-cfn.yaml
@@ -110,10 +110,11 @@ Resources:
           Statement:
             Effect: Allow
             Action: sqs:*
-            Resource: !Split
-              - ","
-              - !ImportValue
-                'Fn::Sub': 'NotificationSenderWorkerQueueArns-${Stage}'
+            Resource: "arn:aws:sqs:eu-west-1:[...]"
       - PolicyName: VPC
         PolicyDocument:
           Statement:
```

After that, the following command should indicate that this value is no longer
imported, and then you can freely make any neccessary changes:

``` shell
$ aws cloudformation list-imports --export-name='NotificationSenderWorkerQueueArns-CODE'
An error occurred (ValidationError) when calling the ListImports operation: Export 'NotificationSenderWorkerQueueArns-CODE' is not imported by any stack.
```

After the project has been redeployed with riff-raff, the import should be
automatically put back in place.


function readStringParameter() {
    print -r `aws --region eu-west-1 --profile mobile ssm get-parameter --name "/notifications/CODE/workers/$1" --query "Parameter.Value"`
}

function readSecureStringParameter() {
    print -r `aws --region eu-west-1 --profile mobile ssm get-parameter --with-decryption --name "/notifications/CODE/workers/$1" --query "Parameter.Value"`
}

print -r "{"
print -r "  "android-beta: {
print -r "    "cleaningSqsUrl:  `readStringParameter "android-beta/cleaningSqsUrl"`,
print -r "    "sqsEc2Url: `readStringParameter "android-beta/sqsUrl"`,
print -r "    "dryrun:  `readStringParameter "android-beta/dryrun"`,
print -r "    "fcm.debug:  `readStringParameter "android-beta/fcm.debug"`,
print -r "    "fcm.serviceAccountKey:  `readSecureStringParameter "android-beta/fcm.serviceAccountKey"`,
print -r "    "fcm.threadPoolSize:  `readStringParameter "android-beta/fcm.threadPoolSize"`,
print -r "    "fcm.allowedTopicsForBatchSend:  `readStringParameter "android-beta/fcm.allowedTopicsForBatchSend"`
print -r "  "},
print -r "  "android-edition: {
print -r "    "fcm.debug:  `readStringParameter "android-edition/fcm.debug"`,
print -r "    "cleaningSqsUrl:  `readStringParameter "android-edition/cleaningSqsUrl"`,
print -r "    "sqsEc2Url: `readStringParameter "android-edition/sqsUrl"`,
print -r "    "dryrun:  `readStringParameter "android-edition/dryrun"`,
print -r "    "fcm.allowedTopicsForBatchSend:  `readStringParameter "android-edition/fcm.allowedTopicsForBatchSend"`,
print -r "    "fcm.threadPoolSize:  `readStringParameter "android-edition/fcm.threadPoolSize"`,
print -r "    "fcm.serviceAccountKey:  `readSecureStringParameter "android-edition/fcm.serviceAccountKey"`
print -r "  "},
print -r "  "android: {
print -r "    "cleaningSqsUrl:  `readStringParameter "android/cleaningSqsUrl"`,
print -r "    "sqsEc2Url: `readStringParameter "android/sqsUrl"`,
print -r "    "dryrun:  `readStringParameter "android/dryrun"`,
print -r "    "fcm.allowedTopicsForBatchSend:  `readStringParameter "android/fcm.allowedTopicsForBatchSend"`,
print -r "    "fcm.debug:  `readStringParameter "android/fcm.debug"`,
print -r "    "fcm.serviceAccountKey:  `readSecureStringParameter "android/fcm.serviceAccountKey"`,
print -r "    "fcm.threadPoolSize:  `readStringParameter "android/fcm.threadPoolSize"`
print -r "  "},
print -r "  "ios-edition: {
print -r "    "apns.keyId:  `readStringParameter "ios-edition/apns.keyId"`,
print -r "    "apns.teamId:  `readStringParameter "ios-edition/apns.teamId"`,
print -r "    "cleaningSqsUrl:  `readStringParameter "ios-edition/cleaningSqsUrl"`,
print -r "    "sqsEc2Url: `readStringParameter "ios-edition/sqsUrl"`,
print -r "    "dryrun:  `readStringParameter "ios-edition/dryrun"`,
print -r "    "apns.bundleId:  `readStringParameter "ios-edition/apns.bundleId"`,
print -r "    "apns.certificate:  `readSecureStringParameter "ios-edition/apns.certificate"`,
print -r "    "apns.sendingToProdServer:  `readStringParameter "ios-edition/apns.sendingToProdServer"`,
print -r "    "apns.threadPoolSize:  `readStringParameter "ios-edition/apns.threadPoolSize"`,
print -r "    "mapi.baseUrl:  `readStringParameter "ios-edition/mapi.baseUrl"`
print -r "  "},
print -r "  "ios: {
print -r "    "apns.certificate:  `readSecureStringParameter "ios/apns.certificate"`,
print -r "    "apns.keyId:  `readStringParameter "ios/apns.keyId"`,
print -r "    "apns.sendingToProdServer:  `readStringParameter "ios/apns.sendingToProdServer"`,
print -r "    "apns.teamId:  `readStringParameter "ios/apns.teamId"`,
print -r "    "cleaningSqsUrl:  `readStringParameter "ios/cleaningSqsUrl"`,
print -r "    "dryrun:  `readStringParameter "ios/dryrun"`,
print -r "    "mapi.baseUrl:  `readStringParameter "ios/mapi.baseUrl"`,
print -r "    "apns.bundleId:  `readStringParameter "ios/apns.bundleId"`,
print -r "    "apns.threadPoolSize:  `readStringParameter "ios/apns.threadPoolSize"`
print -r "  }"
print -r "}"
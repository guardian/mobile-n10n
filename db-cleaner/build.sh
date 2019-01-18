#!/usr/bin/env bash

rm -rf target/
mkdir -p target/lambda/release
mkdir -p target/package/db-cleaner
mkdir -p target/package/cfn
cp riff-raff.yaml target/package/riff-raff.yaml
cp cfn.yaml target/package/cfn/cfn.yaml

PROJECT="mobile-notifications:db-cleaner"
BUILD_START_DATE=$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")

cat > target/build.json << EOF
{
   "projectName":"$PROJECT",
   "buildNumber":"$BUILD_NUMBER",
   "startTime":"$BUILD_START_DATE",
   "revision":"$BUILD_VCS_NUMBER",
   "vcsURL":"git@github.com:guardian/mobile-n10n.git",
   "branch":"$BRANCH_NAME"
}
EOF

echo running docker run --rm -v ${PWD}:/code softprops/lambda-rust:latest
docker run --rm -v ${PWD}:/code softprops/lambda-rust:latest

cd target
cp lambda/release/bootstrap package/db-cleaner/bootstrap

cd package/db-cleaner
zip lambda.zip bootstrap
rm bootstrap
cd ..

#cp cloudformation/discussion-notifications.yaml target/cfn/cfn.yaml

aws s3 cp --acl bucket-owner-full-control --region=eu-west-1 --recursive . s3://riffraff-artifact/$PROJECT/$BUILD_NUMBER
aws s3 cp --acl bucket-owner-full-control --region=eu-west-1 ../build.json s3://riffraff-builds/$PROJECT/$BUILD_NUMBER/build.json

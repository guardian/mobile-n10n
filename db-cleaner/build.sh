#!/usr/bin/env bash

rm -rf target/
rm -rf lambda/
mkdir -p target/lambda/release
mkdir -p target/package/db-cleaner
mkdir -p target/package/cfn
mkdir -p lambda/release/ # for the docker image to write the file
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

docker run --rm -v ${PWD}:/code softprops/lambda-rust:0.2.0-rust-1.31.1

cd target
cp lambda/release/bootstrap package/db-cleaner/bootstrap

cd package/db-cleaner
zip lambda.zip bootstrap
rm bootstrap
cd ..

#cp cloudformation/discussion-notifications.yaml target/cfn/cfn.yaml

aws s3 cp --acl bucket-owner-full-control --region=eu-west-1 --recursive . s3://riffraff-artifact/$PROJECT/$BUILD_NUMBER
aws s3 cp --acl bucket-owner-full-control --region=eu-west-1 ../build.json s3://riffraff-builds/$PROJECT/$BUILD_NUMBER/build.json

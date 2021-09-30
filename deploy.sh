#!/bin/bash

BUILD_NUMBER=${BUILD_NUMBER:-unknown}

# login with docker to the ecr repository

# as per: <https://docs.aws.amazon.com/cli/latest/reference/ecr/get-authorization-token.html>

AUTH_TOKEN=$(aws ecr get-authorization-token --registry-ids ${NOTIFICATION_LAMBDA_REPOSITORY_ID} --output text --query 'authorizationData[].authorizationToken' --region eu-west-1 | base64 --decode | cut -d: -f2)

docker login -u AWS -p ${AUTH_TOKEN} ${NOTIFICATION_LAMBDA_REPOSITORY_URL}

docker push ${NOTIFICATION_LAMBDA_REPOSITORY_URL}:${BUILD_NUMBER}

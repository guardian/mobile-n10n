#!/bin/bash

REPOSITORY_URL=201359054765.dkr.ecr.eu-west-1.amazonaws.com/notificationworkerlambda-image

BUILD_NUMBER=${BUILD_NUMBER:-unknown}

# login with docker to the ecr repository

# as per: <https://docs.aws.amazon.com/cli/latest/reference/ecr/get-authorization-token.html>

AUTH_TOKEN=$(aws ecr get-authorization-token --registry-ids 201359054765 --output text --query 'authorizationData[].authorizationToken' --region eu-west-1 | base64 --decode | cut -d: -f2)

docker login -u AWS -p ${AUTH_TOKEN} ${REPOSITORY_URL}

docker push ${REPOSITORY_URL}:${BUILD_NUMBER}

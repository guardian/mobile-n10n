package com.gu.liveactivities

import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.auth.credentials.{
	AwsCredentialsProviderChain,
	ProfileCredentialsProvider,
	DefaultCredentialsProvider,
}
import software.amazon.awssdk.regions.Region.EU_WEST_1
import org.specs2.mutable.Specification
import org.specs2.specification.{BeforeAfterAll, Scope}
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.services.dynamodb.model._
import java.net.URI
import software.amazon.awssdk.regions.Region

trait DynamodbSpecification extends Specification with BeforeAfterAll {

  sequential

  val TableName: String

  def createTableRequest: CreateTableRequest

  val TestEndpoint = "http://localhost:8002"

  override def beforeAll(): Unit = {
    awsClient.createTable(createTableRequest).join()
  }

  override def afterAll(): Unit = {
    awsClient.deleteTable(DeleteTableRequest.builder().tableName(TableName).build()).join()
  }

  private def awsClient = DynamoDbAsyncClient
      .builder()
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("TEST", "TEST")))
      .region(Region.EU_WEST_1)
      .endpointOverride(URI.create(TestEndpoint))
      .build()

  trait AsyncDynamoScope extends Scope {
    val asyncClient = awsClient
  }
}
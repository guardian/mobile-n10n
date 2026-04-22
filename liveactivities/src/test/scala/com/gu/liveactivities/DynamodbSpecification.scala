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
import org.scanamo.ScanamoAsync
import scala.concurrent.ExecutionContext
import org.scanamo.LocalDynamoDB

trait DynamodbSpecification extends Specification with BeforeAfterAll {

  sequential

  implicit val ec: ExecutionContext = ExecutionContext.global

  val TableName: String

  def targetTableAttributes: Seq[(String, ScalarAttributeType)]

  val TestEndpoint = "http://localhost:8002"

  override def beforeAll(): Unit = {
    LocalDynamoDB.createTable(awsClient)(TableName)(targetTableAttributes: _*)   
  }

  override def afterAll(): Unit = {
    LocalDynamoDB.deleteTable(awsClient)(TableName)
  }

  private def awsClient = LocalDynamoDB.client(8002)

  private def scanamo = ScanamoAsync(awsClient)

  trait AsyncDynamoScope extends Scope {
    val asyncClient = awsClient
  }
}
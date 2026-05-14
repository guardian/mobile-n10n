package com.gu.liveactivities

import org.specs2.mutable.Specification
import org.specs2.specification.{BeforeAfterAll,BeforeAfterEach, Scope}
import software.amazon.awssdk.services.dynamodb.model._

import org.scanamo.ScanamoAsync
import scala.concurrent.ExecutionContext
import org.scanamo.LocalDynamoDB

trait DynamodbSpecification extends Specification with BeforeAfterAll with BeforeAfterEach {

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
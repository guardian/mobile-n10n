package tracking

import aws.AsyncDynamo
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient
import com.amazonaws.services.dynamodbv2.model.{CreateTableRequest, DeleteTableRequest}
import org.specs2.mutable.Specification
import org.specs2.specification.{Scope, BeforeAfterAll}

trait DynamodbSpecification extends Specification with BeforeAfterAll {

  sequential

  val TableName: String

  def createTableRequest: CreateTableRequest

  val TestEndpoint = "http://localhost:8000"

  override def beforeAll() = {
    awsClient.createTable(createTableRequest)
  }

  override def afterAll() = {
    awsClient.deleteTable(new DeleteTableRequest(TableName))
  }

  private def awsClient = {
    val client = new AmazonDynamoDBAsyncClient(new DefaultAWSCredentialsProviderChain())
    client.setEndpoint(TestEndpoint)
    client
  }

  trait AsyncDynamoScope extends Scope {
    val asyncClient = new AsyncDynamo(awsClient)
  }
}
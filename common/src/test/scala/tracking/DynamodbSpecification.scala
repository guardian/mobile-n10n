package tracking

import java.net.URI

import aws.AsyncDynamo
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{CreateTableRequest, DeleteTableRequest}
import org.specs2.mutable.Specification
import org.specs2.specification.{Scope, BeforeAfterAll}

trait DynamodbSpecification extends Specification with BeforeAfterAll {

  sequential

  val TableName: String

  def createTableRequest: CreateTableRequest

  val TestEndpoint = "http://localhost:8000"

  override def beforeAll(): Unit = {
    awsClient.createTable(createTableRequest).join()
    ()
  }

  override def afterAll(): Unit = {
    awsClient.deleteTable(DeleteTableRequest.builder().tableName(TableName).build()).join()
    ()
  }

  private def awsClient = {
    DynamoDbAsyncClient.builder()
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("DUMMY", "DUMMY")))
      .endpointOverride(URI.create(TestEndpoint))
      .region(Region.EU_WEST_1)
      .build()
  }

  trait AsyncDynamoScope extends Scope {
    val asyncClient = new AsyncDynamo(awsClient)
  }
}
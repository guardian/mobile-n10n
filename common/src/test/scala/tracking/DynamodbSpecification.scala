package tracking

import aws.AsyncDynamo
import com.amazonaws.auth.{AWSCredentials, AWSCredentialsProvider, AWSCredentialsProviderChain}
import com.amazonaws.client.builder.AwsAsyncClientBuilder
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBAsyncClient, AmazonDynamoDBAsyncClientBuilder}
import com.amazonaws.services.dynamodbv2.model.{CreateTableRequest, DeleteTableRequest}
import org.specs2.mutable.Specification
import org.specs2.specification.{Scope, BeforeAfterAll}

trait DynamodbSpecification extends Specification with BeforeAfterAll {

  sequential

  val TableName: String

  def createTableRequest: CreateTableRequest

  val TestEndpoint = "http://localhost:8000"

  override def beforeAll(): Unit = {
    awsClient.createTable(createTableRequest)
  }

  override def afterAll(): Unit = {
    awsClient.deleteTable(new DeleteTableRequest(TableName))
  }

  private def awsClient = {
    val chain = new AWSCredentialsProviderChain(new AWSCredentialsProvider {
      override def refresh(): Unit = {}

      override def getCredentials: AWSCredentials = new AWSCredentials {
        override def getAWSAccessKeyId: String = ""

        override def getAWSSecretKey: String = ""
      }
    })

    val client = AmazonDynamoDBAsyncClientBuilder.standard()
      .withCredentials(chain)
      .withEndpointConfiguration( new EndpointConfiguration(TestEndpoint, Regions.EU_WEST_1.getName) )
      .build

    client
  }

  trait AsyncDynamoScope extends Scope {
    val asyncClient = new AsyncDynamo(awsClient)
  }
}
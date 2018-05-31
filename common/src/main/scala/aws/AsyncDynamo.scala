package aws

import com.amazonaws.auth.{AWSCredentialsProvider, DefaultAWSCredentialsProviderChain}
import com.amazonaws.regions.Regions
import com.amazonaws.regions.Regions.EU_WEST_1
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBAsync, AmazonDynamoDBAsyncClientBuilder}
import com.amazonaws.services.dynamodbv2.model._

import scala.concurrent.Future

object AsyncDynamo {
  def keyEquals(s: String): Condition = new Condition()
    .withComparisonOperator(ComparisonOperator.EQ)
    .withAttributeValueList(new AttributeValue(s))

  def keyGE(s: String): Condition = new Condition()
    .withComparisonOperator(ComparisonOperator.GE)
    .withAttributeValueList(new AttributeValue(s))

  def keyLT(s: String): Condition = new Condition()
    .withComparisonOperator(ComparisonOperator.LT)
    .withAttributeValueList(new AttributeValue(s))

  def keyBetween(a: String, b: String): Condition = new Condition()
    .withComparisonOperator(ComparisonOperator.BETWEEN)
    .withAttributeValueList(new AttributeValue(a), new AttributeValue(b))

  def apply(regions: Regions, credentialsProvider: AWSCredentialsProvider = new DefaultAWSCredentialsProviderChain): AsyncDynamo = {
    val dynamoClient: AmazonDynamoDBAsync = AmazonDynamoDBAsyncClientBuilder.standard()
      .withCredentials(credentialsProvider)
      .withRegion(regions.getName)
      .build()

    new AsyncDynamo(dynamoClient)
  }
}

class AsyncDynamo(val client: AmazonDynamoDBAsync) {
  import AWSAsync._
  // These work but are red because Intellij is broken
  def putItem(request: PutItemRequest): Future[PutItemResult] = wrapAsyncMethod(client.putItemAsync, request)
  def query(request: QueryRequest): Future[QueryResult] = wrapAsyncMethod(client.queryAsync, request)
  def updateItem(request: UpdateItemRequest): Future[UpdateItemResult] = wrapAsyncMethod(client.updateItemAsync, request)
}

package aws

import aws.AWSAsync.wrapCompletableFuture
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._

import scala.concurrent.Future

object AsyncDynamo {
  def keyEquals(s: String): Condition = Condition.builder()
    .comparisonOperator(ComparisonOperator.EQ)
    .attributeValueList(AttributeValue.builder().s(s).build())
    .build()

  def keyGE(s: String): Condition = Condition.builder()
    .comparisonOperator(ComparisonOperator.GE)
    .attributeValueList(AttributeValue.builder().s(s).build())
    .build()

  def keyLT(s: String): Condition = Condition.builder()
    .comparisonOperator(ComparisonOperator.LT)
    .attributeValueList(AttributeValue.builder().s(s).build())
    .build()

  def keyBetween(a: String, b: String): Condition = Condition.builder()
    .comparisonOperator(ComparisonOperator.BETWEEN)
    .attributeValueList(AttributeValue.builder().s(a).build(), AttributeValue.builder().s(b).build())
    .build()

  def apply(region: Region, credentialsProvider: AwsCredentialsProvider): AsyncDynamo = {
    val dynamoClient: DynamoDbAsyncClient = DynamoDbAsyncClient.builder()
      .credentialsProvider(credentialsProvider)
      .region(region)
      .build()

    new AsyncDynamo(dynamoClient)
  }
}

class AsyncDynamo(val client: DynamoDbAsyncClient) {
  def putItem(request: PutItemRequest): Future[PutItemResponse] = wrapCompletableFuture(client.putItem(request))
  def query(request: QueryRequest): Future[QueryResponse] = wrapCompletableFuture(client.query(request))
  def get(request: GetItemRequest): Future[GetItemResponse] = wrapCompletableFuture(client.getItem(request))
  def updateItem(request: UpdateItemRequest): Future[UpdateItemResponse] = wrapCompletableFuture(client.updateItem(request))
}

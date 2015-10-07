package aws

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient
import com.amazonaws.services.dynamodbv2.model._

import scala.concurrent.Future

object AsyncDynamo {
  def keyEquals(s: String) = new Condition()
    .withComparisonOperator(ComparisonOperator.EQ)
    .withAttributeValueList(new AttributeValue(s))

  def keyGE(s: String) = new Condition()
    .withComparisonOperator(ComparisonOperator.GE)
    .withAttributeValueList(new AttributeValue(s))

  def keyLT(s: String) = new Condition()
    .withComparisonOperator(ComparisonOperator.LT)
    .withAttributeValueList(new AttributeValue(s))
}

class AsyncDynamo(client: AmazonDynamoDBAsyncClient) {
  import AWSAsync._
  // These work but are red because Intellij is broken
  def putItem(putItemRequest: PutItemRequest): Future[PutItemResult] = wrapAsyncMethod(client.putItemAsync, putItemRequest)
  def query(queryRequest: QueryRequest): Future[QueryResult] = wrapAsyncMethod(client.queryAsync, queryRequest)
}
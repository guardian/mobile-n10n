package com.gu.notificationschedule.dynamo

import java.net.URI

import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterEach

class NotificationSchedulePersistenceImplSpec extends Specification with BeforeAfterEach {
  val tableName = "test-table"
  var maybeClient: Option[DynamoDbAsyncClient] = None
  "NotificationSchedulePersistence" should {
    "read" in {

      val notificationSchedulePersistence = maybeClient.map(new NotificationSchedulePersistenceImpl(tableName, _)).getOrElse(throw new IllegalStateException())
      notificationSchedulePersistence.writeAsync(NotificationsScheduleEntry("test-uuid", "test-notification", 0L, 0L), None)
      val entries: Seq[NotificationsScheduleEntry] = notificationSchedulePersistence.querySync()
      entries must not be empty
    }
  }

  override def after: Any = {
    maybeClient.foreach(_.deleteTable(DeleteTableRequest.builder().tableName(tableName).build()).join())

  }

  override def before: Any = {
    val client = DynamoDbAsyncClient.builder()
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("DUMMY", "DUMMY")))
      .endpointOverride(URI.create("http://localhost:8001"))
      .region(Region.EU_WEST_1)
      .build()
    val createTableRequest = CreateTableRequest.builder()
      .tableName(tableName)
      .keySchema(KeySchemaElement.builder().attributeName("uuid").keyType(KeyType.HASH).build())
      .attributeDefinitions(
        AttributeDefinition.builder().attributeName("uuid").attributeType(ScalarAttributeType.S).build(),
        AttributeDefinition.builder().attributeName("sent").attributeType(ScalarAttributeType.S).build(),
        AttributeDefinition.builder().attributeName("due_epoch_s").attributeType(ScalarAttributeType.N).build()
      )
      .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(1L).writeCapacityUnits(1L).build())
      .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
        .indexName("due_epoch_s_and_sent")
        .keySchema(
          KeySchemaElement.builder().attributeName("sent").keyType(KeyType.HASH).build(),
          KeySchemaElement.builder().attributeName("due_epoch_s").keyType(KeyType.RANGE).build()
        )
        .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(1L).writeCapacityUnits(1L).build())
        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
        .build()
      )
      .build()
    client.createTable(createTableRequest).join()
    maybeClient = Some(client)


  }
}

package com.gu.notificationschedule.dynamo

import com.amazonaws.auth.{AWSCredentials, AWSCredentialsProvider, AWSCredentialsProviderChain}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.model._
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBAsync, AmazonDynamoDBAsyncClientBuilder}
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterEach

import scala.collection.JavaConverters._

class NotificationSchedulePersistenceImplSpec extends Specification with BeforeAfterEach {
  val tableName = "test-table"
  val chain = new AWSCredentialsProviderChain(new AWSCredentialsProvider {
    override def refresh(): Unit = {}

    override def getCredentials: AWSCredentials = new AWSCredentials {
      override def getAWSAccessKeyId: String = ""

      override def getAWSSecretKey: String = ""
    }
  })
  var maybeClient: Option[AmazonDynamoDBAsync] = None
  "NotificationSchedulePersistence" should {
    "read" in {

      val notificationSchedulePersistence = maybeClient.map(new NotificationSchedulePersistenceImpl(tableName, _)).getOrElse(throw new IllegalStateException())
      notificationSchedulePersistence.writeAsync(NotificationsScheduleEntry("test-uuid", "test-notification", 0L, 0L), None)
      val entries: Seq[NotificationsScheduleEntry] = notificationSchedulePersistence.querySync()
      entries must not be empty
    }
  }

  override def after: Any = {
    maybeClient.foreach(_.deleteTable(tableName))

  }

  override def before: Any = {
    val client = AmazonDynamoDBAsyncClientBuilder.standard()
      .withCredentials(chain)
      .withEndpointConfiguration(new EndpointConfiguration("http://localhost:8001", Regions.EU_WEST_1.getName))
      .build
    val createTableRequest = new CreateTableRequest()
      .withTableName(tableName)
      .withKeySchema(new KeySchemaElement("uuid", KeyType.HASH))
      .withAttributeDefinitions(List(
        new AttributeDefinition("uuid", ScalarAttributeType.S),
        new AttributeDefinition("sent", ScalarAttributeType.S),
        new AttributeDefinition("due_epoch_s", ScalarAttributeType.N)
      ).asJava)
      .withProvisionedThroughput(new ProvisionedThroughput(1L, 1L))
      .withGlobalSecondaryIndexes(new GlobalSecondaryIndex()
        .withIndexName("due_epoch_s_and_sent")
        .withKeySchema(List(
          new KeySchemaElement("sent", KeyType.HASH),
          new KeySchemaElement("due_epoch_s", KeyType.RANGE)
        ).asJava)
        .withProvisionedThroughput(new ProvisionedThroughput(1L, 1L))
        .withProjection(new Projection().withProjectionType(ProjectionType.ALL))
      )
    client.createTable(createTableRequest)
    maybeClient = Some(client)


  }
}

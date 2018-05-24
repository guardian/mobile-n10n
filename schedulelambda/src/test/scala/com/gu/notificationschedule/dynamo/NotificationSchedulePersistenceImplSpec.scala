package com.gu.notificationschedule.dynamo

import com.amazonaws.auth.{AWSCredentials, AWSCredentialsProvider, AWSCredentialsProviderChain}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.model._
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBAsync, AmazonDynamoDBAsyncClientBuilder}
import com.gu.notificationschedule.NotificationScheduleConfig
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterEach

import scala.collection.JavaConverters._
import scala.collection.mutable

class NotificationSchedulePersistenceImplSpec extends Specification with BeforeAfterEach {
  val config = NotificationScheduleConfig("test-app", "test-stage", "test-stack")
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

      val notificationSchedulePersistence = maybeClient.map(new NotificationSchedulePersistenceImpl(config, _)).getOrElse(throw new IllegalStateException())
      notificationSchedulePersistence.write(NotificationsScheduleEntry("test-uuid", "test-notification", 0L, 0L), false, 0L)
      val entries: Seq[NotificationsScheduleEntry] = notificationSchedulePersistence.query()
      entries must not be empty
    }
  }

  override def after: Any = {
    println("after")
    maybeClient.foreach(_.deleteTable(config.notificationScheduleTable))

  }

  override def before: Any = {
    println("Before")
    val client = AmazonDynamoDBAsyncClientBuilder.standard()
      .withCredentials(chain)
      .withEndpointConfiguration(new EndpointConfiguration("http://localhost:8000", Regions.EU_WEST_1.getName))
      .build
    val createTableRequest = new CreateTableRequest()
      .withTableName(config.notificationScheduleTable)
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

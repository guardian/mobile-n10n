package tracking

import aws.AsyncDynamo
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient
import com.amazonaws.services.dynamodbv2.model._
import models._
import org.joda.time.{Interval, DateTimeZone, DateTime}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.ShouldMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll
import org.specs2.specification.Scope
import tracking.Repository.RepositoryResult

import scala.collection.JavaConversions._
import scala.concurrent.Future

class DynamoNotificationReportRepositoryTest(implicit ev: ExecutionEnv) extends Specification with Mockito with ShouldMatchers with BeforeAfterAll {

  sequential

  override def beforeAll() = createTable()
  override def afterAll() = destroyTable()

  "A DynamoNotificationReportRepository" should {
    "store and retrieve a NotificationReport" in new RepositoryScope with ExampleReports {
      afterStoringReports(List(singleReport)) {
        repository.getByUuid(singleReport.uuid)
      } must beEqualTo(singleReport).await
    }

    "get NotificationReports by date range" in new RepositoryScope with ExampleReports {
      afterStoringReports(reports) {
        repository.getByTypeWithDateRange(
          notificationType = "test-type",
          from = interval.getStart,
          to = interval.getEnd
        )
      } must beEqualTo(reportsInInterval.toSet).await
    }
  }

  private val TableName = "test-table"
  private val TestEndpoint = "http://localhost:8000"
  private val UuidField = "uuid"
  private val SentTimeField = "sentTime"
  private val TypeField = "type"
  private val SentTimeIndex = "sentTime-index"

  trait RepositoryScope extends Scope {
    val asyncClient = {
      val client = new AmazonDynamoDBAsyncClient(new DefaultAWSCredentialsProviderChain())
      client.setEndpoint(TestEndpoint)
      new AsyncDynamo(client)
    }

    val repository = new DynamoNotificationReportRepository(asyncClient, TableName)
    
    def afterStoringReports[T](reports: List[NotificationReport])(fn: => Future[RepositoryResult[T]]): Future[T] = {
      Future.sequence(reports map repository.store) flatMap { _ => fn.map(_.toOption.get) }
    }
  }

  trait ExampleReports {
    val singleReport = createNotificationReport(uuid = "test-uuid-0001", sentTime = "2015-01-01T10:11:12Z")

    val reports = List(
      createNotificationReport(uuid = "test-uuid-0001", sentTime = "2015-01-01T10:11:12Z"),
      createNotificationReport(uuid = "test-uuid-0002", sentTime = "2015-01-02T10:11:12Z"),
      createNotificationReport(uuid = "test-uuid-0003", sentTime = "2015-01-03T10:11:12Z"),
      createNotificationReport(uuid = "test-uuid-0004", sentTime = "2015-01-04T10:11:12Z"),
      createNotificationReport(uuid = "test-uuid-0005", sentTime = "2015-01-05T10:11:12Z"),
      createNotificationReport(uuid = "test-uuid-0006", sentTime = "2015-01-06T10:11:12Z")
    )

    val interval = new Interval(DateTime.parse("2015-01-02T00:00:00Z"), DateTime.parse("2015-01-05T12:00:00Z"))

    val reportsInInterval = reports.filter(report => interval.contains(report.sentTime))

    private def createNotificationReport(uuid: String, sentTime: String) = NotificationReport.create(
      sentTime = DateTime.parse(sentTime).withZone(DateTimeZone.UTC),
      notification = Notification(
        uuid = uuid,
        sender = "some-sender",
        timeToLiveInSeconds = 1,
        payload = MessagePayload(
          link = Some("some-link"),
          `type` = Some("test-type"),
          ticker = Some("some-ticker"),
          title = Some("some-title"),
          message = Some("some-message")
        )
      ),
      statistics = NotificationStatistics(Map(WindowsMobile -> Some(5)))
    )
  }

  def createTable() = {
    val awsClient = new AmazonDynamoDBAsyncClient(new DefaultAWSCredentialsProviderChain())
    awsClient.setEndpoint(TestEndpoint)

    val createTableRequest = {
      val sentTimeIndex = new GlobalSecondaryIndex()
      sentTimeIndex.setIndexName(SentTimeIndex)
      sentTimeIndex.setKeySchema(List(
        new KeySchemaElement(TypeField, KeyType.HASH),
        new KeySchemaElement(SentTimeField, KeyType.RANGE)
      ))
      sentTimeIndex.setProvisionedThroughput(new ProvisionedThroughput(5L, 5L))
      sentTimeIndex.withProjection(new Projection().withProjectionType(ProjectionType.ALL))

      val req = new CreateTableRequest(TableName, List(new KeySchemaElement(UuidField, KeyType.HASH)))
      req.setAttributeDefinitions(List(
        new AttributeDefinition(UuidField, ScalarAttributeType.S),
        new AttributeDefinition(SentTimeField, ScalarAttributeType.S),
        new AttributeDefinition(TypeField, ScalarAttributeType.S)
      ))
      req.setProvisionedThroughput(new ProvisionedThroughput(5L, 5L))
      req.setGlobalSecondaryIndexes(List(sentTimeIndex))
      req
    }
    awsClient.createTable(createTableRequest)
  }

  def destroyTable() = {
    val awsClient = new AmazonDynamoDBAsyncClient(new DefaultAWSCredentialsProviderChain())
    awsClient.setEndpoint(TestEndpoint)
    awsClient.deleteTable(new DeleteTableRequest(TableName))
  }
}

package tracking

import java.util.UUID

import com.amazonaws.services.dynamodbv2.model._
import models._
import org.joda.time.{Interval, DateTimeZone, DateTime}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import tracking.Repository.RepositoryResult

import scala.collection.JavaConversions._
import scala.concurrent.Future

class DynamoNotificationReportRepositorySpec(implicit ev: ExecutionEnv) extends DynamodbSpecification with Mockito {

  override val TableName = "test-table"

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

  trait RepositoryScope extends AsyncDynamoScope {
    val repository = new DynamoNotificationReportRepository(asyncClient, TableName)
    
    def afterStoringReports[T](reports: List[NotificationReport])(fn: => Future[RepositoryResult[T]]): Future[T] = {
      Future.sequence(reports map repository.store) flatMap { _ => fn.map(_.toOption.get) }
    }
  }

  trait ExampleReports {
    val singleReport = createNotificationReport(uuid = UUID.randomUUID(), sentTime = "2015-01-01T10:11:12Z")

    val reports = List(
      createNotificationReport(uuid = UUID.randomUUID(), sentTime = "2015-01-01T10:11:12Z"),
      createNotificationReport(uuid = UUID.randomUUID(), sentTime = "2015-01-02T10:11:12Z"),
      createNotificationReport(uuid = UUID.randomUUID(), sentTime = "2015-01-03T10:11:12Z"),
      createNotificationReport(uuid = UUID.randomUUID(), sentTime = "2015-01-04T10:11:12Z"),
      createNotificationReport(uuid = UUID.randomUUID(), sentTime = "2015-01-05T10:11:12Z"),
      createNotificationReport(uuid = UUID.randomUUID(), sentTime = "2015-01-06T10:11:12Z")
    )

    val interval = new Interval(DateTime.parse("2015-01-02T00:00:00Z"), DateTime.parse("2015-01-05T12:00:00Z"))

    val reportsInInterval = reports.filter(report => interval.contains(report.sentTime))

    private def createNotificationReport(uuid: UUID, sentTime: String) = NotificationReport.create(
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

  def createTableRequest = {
    val UuidField = "uuid"
    val SentTimeField = "sentTime"
    val TypeField = "type"
    val SentTimeIndex = "sentTime-index"

    val sentTimeIndex = new GlobalSecondaryIndex()
      .withIndexName(SentTimeIndex)
      .withKeySchema(List(
        new KeySchemaElement(TypeField, KeyType.HASH),
        new KeySchemaElement(SentTimeField, KeyType.RANGE)
      ))
      .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L))
      .withProjection(new Projection().withProjectionType(ProjectionType.ALL))

    new CreateTableRequest(TableName, List(new KeySchemaElement(UuidField, KeyType.HASH)))
      .withAttributeDefinitions(List(
        new AttributeDefinition(UuidField, ScalarAttributeType.S),
        new AttributeDefinition(SentTimeField, ScalarAttributeType.S),
        new AttributeDefinition(TypeField, ScalarAttributeType.S)
      ))
      .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L))
      .withGlobalSecondaryIndexes(List(sentTimeIndex))
  }
}

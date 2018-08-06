package azure

import java.util.concurrent.atomic.AtomicInteger

import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.BuiltInComponents
import play.api.test._
import play.api.mvc._
import play.api.routing.sird._
import play.core.server.Server

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class NotificationHubClientSpec(implicit ee: ExecutionEnv) extends Specification with Mockito {

  "Azure hub client" should {

    "send a notification" in {
      val rawPush = GCMRawPush(GCMBody(data = Map.empty), None)
      val result = withHubClient { cs => {
        case POST(p"/messages/") => cs.defaultActionBuilder(Results.Ok)
      }} { client =>
        client.sendNotification(rawPush)
      }

      result should beRight
    }

    "fail to send a notification if azure refuses to send it" in {
      val rawPush = GCMRawPush(GCMBody(data = Map.empty), None)
      var tryCount = 0
      val result = withHubClient { cs => {
        case POST(p"/messages/") => cs.defaultActionBuilder(Results.ServiceUnavailable(errorResponse))
      }} { client =>
        client.sendNotification(rawPush)
      }

      result should beLeft
    }

    "try to send a notification twice if the first try fails" in {
      val rawPush = GCMRawPush(GCMBody(data = Map.empty), None)
      val tryCount = new AtomicInteger(0)
      val result = withHubClient { cs => {
        case POST(p"/messages/") =>
          tryCount.incrementAndGet()
          if (tryCount.intValue <= 1) {
            cs.defaultActionBuilder(Results.ServiceUnavailable(errorResponse))
          } else {
            cs.defaultActionBuilder(Results.Ok)
          }
      }} { client =>
        client.sendNotification(rawPush)
      }

      result should beRight
    }

    "Fails after trying three times" in {
      val rawPush = GCMRawPush(GCMBody(data = Map.empty), None)
      var tryCount = new AtomicInteger(0)
      val result = withHubClient { cs => {
        case POST(p"/messages/") =>
          tryCount.incrementAndGet()
          if (tryCount.intValue <= 3) {
            cs.defaultActionBuilder(Results.ServiceUnavailable(errorResponse))
          } else {
            cs.defaultActionBuilder(Results.Ok)
          }
      }} { client =>
        client.sendNotification(rawPush)
      }

      result should beLeft
    }

    "send job request" in {
      val result = withHubClient { cs => {
        case POST(p"/jobs") => cs.defaultActionBuilder(Results.Created(jobResponse))
      }} { client =>
        val job = NotificationHubJobRequest(NotificationHubJobType.ExportRegistrations)
        client.submitNotificationHubJob(job)
      }
      result should beRight
    }
  }

  val jobResponse =
    <entry xmlns="http://www.w3.org/2005/Atom">
      <id>https://guardian-windows-10-live-ns.servicebus.windows.net/guardian-notification-prod/jobs/25193755060864937755f902a59-2756-4735-9feb-0ebc5fe59c94?api-version=2015-01</id> <title type="text">25193755060864937755f902a59-2756-4735-9feb-0ebc5fe59c94</title> <published>2016-06-01T03:03:11Z</published> <updated>2016-06-01T03:03:11Z</updated> <link rel="self" href="https://guardian-windows-10-live-ns.servicebus.windows.net/guardian-notification-prod/jobs/25193755060864937755f902a59-2756-4735-9feb-0ebc5fe59c94?api-version=2015-01"/>
      <content type="application/xml">
        <NotificationHubJob xmlns="http://schemas.microsoft.com/netservices/2010/10/servicebus/connect" xmlns:i="http://www.w3.org/2001/XMLSchema-instance">
          <JobId>25193755060864937755f902a59-2756-4735-9feb-0ebc5fe59c94</JobId>
          <Type>ExportRegistrations</Type>
          <OutputContainerUri>https://guhubbackup.blob.core.windows.net/backups-code</OutputContainerUri>
          <CreatedAt>2016-06-01T03:03:11.4912228Z</CreatedAt>
          <UpdatedAt>2016-06-01T03:03:11.4912228Z</UpdatedAt>
        </NotificationHubJob>
      </content>
    </entry>

  val errorResponse =
    <entry xmlns="http://www.w3.org/2005/Atom">
      <Code>503</Code>
      <Detail>Azure Notifications Hub Server Busy</Detail>
    </entry>

  def withHubClient[T](routes: BuiltInComponents => PartialFunction[RequestHeader, Handler])(block: NotificationHubClient => Future[T]): T = {
      Server.withRouterFromComponents()(routes){ implicit port =>
      val connection = NotificationHubConnection(s"http://localhost:$port", "sharedKeyName", "sharedKey")
      WsTestClient.withClient { client =>
        Await.result(block(new NotificationHubClient(connection, client)), 5.seconds)
      }
    }
  }
  
}

package azure

import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import play.api.Play
import play.api.test._
import play.api.mvc._
import play.api.routing.sird._
import play.core.server.Server
import scala.concurrent.duration._

import scala.xml.Elem

class NotificationHubClientSpec(implicit ee: ExecutionEnv) extends Specification {
  // problems with Materializer being closed when NettyServer tris to bind channel
  args(skipAll = true)

  "Azure hub client" should {
    "send job request" in {
      withHubClient(hubResponse) { client =>
        val job = NotificationHubJobRequest(NotificationHubJobType.ExportRegistrations)

        client.submitNotificationHubJob(job).map { _.isRight } must beTrue.awaitFor(5 seconds)
      }
    }
  }

  val hubResponse =
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

  def withHubClient[T](response: Elem)(block: NotificationHubClient => T): T = {
    Server.withRouter() {
      case POST(p"/jobs") => Action { Results.Created(response) }
    } { implicit port =>
      val connection = NotificationHubConnection(s"http://localhost:$port", "sharedKeyName", "sharedKey")
      WsTestClient.withClient { client =>
        block(new NotificationHubClient(connection, client))
      }
    }
  }
  
}

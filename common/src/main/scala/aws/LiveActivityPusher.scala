package aws

import com.gu.mobile.notifications.client.models.liveActitivites._
import org.slf4j.Logger
import play.api.libs.json.Json
import software.amazon.awssdk.services.eventbridge.EventBridgeClient
import software.amazon.awssdk.services.eventbridge.model.{PutEventsRequest, PutEventsRequestEntry, PutEventsResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class LiveActivityPusher(eventBusName: String, logger: Logger) {
  private val eventBridgeClient =
    EventBridgeClient
      .builder()
      .build()

  def pushEvents(events: List[LiveActivityPayload])(implicit ec: ExecutionContext): Future[Unit] = {

    logger.info(
      "Eventbus pusher: number of events to push: " + events.size
    )
    Future.traverse(events)(pushToEventbus).map(_ => ())
  }

  def pushToEventbus(payload: LiveActivityPayload)(implicit ec: ExecutionContext): Future[Unit] = {

    logger.info(
      s"Eventbus pusher: Processing event with id ${payload.id}"
    )
      val result = Try {
        val entry = PutEventsRequestEntry
          .builder()
          .source("football-lambda")
          .detailType(payload.eventType.asString)
          .detail(Json.toJson(payload).toString())
          .eventBusName(eventBusName)
          .build()

        val request = PutEventsRequest
          .builder()
          .entries(entry)
          .build()

        val response: PutEventsResponse = eventBridgeClient.putEvents(request)

        if (response.failedEntryCount() > 0) {
          val error = response.entries().get(0).errorMessage()
          throw new RuntimeException(s"EventBridge rejected entry: $error")
        } else {
          logger.info(
            s"Eventbus pusher: Event published with event ${payload.eventType} is ${payload.id}"
          )
        }
      }

      result match {
        case Success(_) =>
          logger.info(
            s"Eventbus pusher: Successfully processed live activities event ${payload.eventType} with id ${payload.id}"
          )
          Future.successful(())
        case Failure(e) =>
          logger.error(
            s"Eventbus pusher: Failed to publish live activities event ${payload.eventType} with id ${payload.id}: ${e.getMessage}"
          )
          Future.failed(e)
      }
  }
}

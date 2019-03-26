package com.gu.mobile.notifications.client

import java.util.UUID

import com.gu.mobile.notifications.client.models.{BreakingNewsPayload, NotificationPayload, Topic}
import com.sun.net.httpserver.Authenticator.{Failure, Success}
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class NextGenResponse(id: String)

object NextGenResponse {
  implicit val jf = Json.format[NextGenResponse]
}

protected class NextGenApiClient(
  val host: String,
  val apiKey: String,
  val httpProvider: HttpProvider,
  val clientId: String = "nextGen"
) extends SimpleHttpApiClient {

  private val url = s"$host/push/topic"

  override def send(notificationPayload: NotificationPayload)(implicit ec: ExecutionContext): Future[Either[ApiClientError, Unit]] = {

    def doSend(payload: NotificationPayload): Future[Either[ApiClientError, Unit]] = {
      val json = Json.stringify(Json.toJson(payload))
      postJson(url, json) map {
        case error: HttpError => Left(ApiHttpError(error.status, Some(error.body)))
        case HttpOk(201, body) => validateFormat[NextGenResponse](body)
        case HttpOk(code, body) => Left(UnexpectedApiResponseError(s"Server returned status code $code and body:$body"))
      } recover {
        case e: Exception => Left(HttpProviderError(e))
      }
    }

    def doSendOncePerTopic(breakingNewsPayload: BreakingNewsPayload): Future[Either[ApiClientError, Unit]] = {
      val editions = List("uk", "international", "us", "au")
      val orderedTopicsToSend = editions.flatMap {
        ed => breakingNewsPayload.topic.find(_.name == s"breaking/${ed}")
      }
      val orderedPayloads = orderedTopicsToSend.map { topic => breakingNewsPayload.copy(topic = List(topic), id =UUID.randomUUID()) }

        val responsesFuture = orderedPayloads.foldLeft(Future.successful(Seq.empty[Either[ApiClientError, Unit]])) {
        (futureResults, payload) => futureResults.flatMap{ results => {
          doSend(payload).transformWith {
            case Success(result) => Future.successful(results :+ result)
            case Failure(error) => Future.successful(results :+ Left(HttpProviderError(error) ))
          }
          }
        }
      }

      responsesFuture.map { responses =>
        responses.collect { case Left(error) => error } match {
          case Nil => Right(())
          case onlyOneError :: Nil => Left(onlyOneError)
         ` case errors: List[ApiClientError] => Left(UnexpectedApiResponseError(errors.map(_.description).mkString(", ")))
        }
      }
    }

    notificationPayload match {
      case breakingNews: BreakingNewsPayload if breakingNews.topic.size > 1  => doSendOncePerTopic(breakingNews)
      case _ => doSend(notificationPayload)
    }
  }

}


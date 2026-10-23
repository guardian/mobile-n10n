package com.gu.notifications.worker.utils

import cats.effect.IO
import com.gu.notifications.worker.delivery.DeliveryException.{FailedAPNSDelivery, FailedRequest, GenericFailure, InvalidToken}
import com.gu.notifications.worker.delivery.{DeliveryClient, DeliveryException, DeliverySuccess}
import models.{Notification, NotificationType}
import org.slf4j.{Logger, LoggerFactory}


// Reporting logs once per token after retries. (Sending client logs are once per attempted send)
object Reporting extends Logging {
  def logger: Logger = LoggerFactory.getLogger(this.getClass)

  def notificationTypeForObservability(notification: Notification): String = notification.`type` match {
    case NotificationType.BreakingNews => "breakingNews"
    case _ => "other"
  }

  private def failureFields(e: DeliveryException): Map[String, Any] = {
    val base = Map[String, Any](
      "notificationId" -> e.notificationId.toString,
      "worker.failureType" -> e.getClass.getSimpleName,
    )
    base ++ (e match {
      case InvalidToken(_, token, reason, ts) =>
        Map("token" -> token, "worker.failureReason" -> reason) ++ ts.map(t => "worker.tokenInvalidationTimestamp" -> t.toString)
      case FailedAPNSDelivery(_, _, reason) => Map("worker.failureReason" -> reason)
      case FailedRequest(_, _, cause, code) => Map("worker.failureCause" -> cause.getClass.getSimpleName) ++ code.map("worker.errorCode" -> _)
      case GenericFailure(_, _, underlying) => Map("worker.failureCause" -> underlying.getClass.getSimpleName)
      case _ => Map.empty
    })
  }


  private def logMatchCase(response: Either[DeliveryException, DeliverySuccess], prefix: String)(implicit logger: Logger): Unit = response match {
    case Left(e) =>
      val fields = failureFields(e)
      e match {
        case _: InvalidToken => logger.warn(fields, s"$prefix $e")
        case f: FailedRequest => logger.warn(fields, s"$prefix $e", f.cause)
        case _ => logger.error(fields, prefix, e)
      }
    case Right(_) => () // do nothing on success
  }

  def log[C <: DeliveryClient](prefix: String)(implicit logger: Logger): Either[DeliveryException, DeliverySuccess] => IO[Unit] = { resp =>
    IO.delay {
      logMatchCase(resp, prefix)
    }
  }
}

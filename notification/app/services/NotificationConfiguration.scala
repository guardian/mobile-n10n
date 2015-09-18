package services

import javax.inject.Inject

import play.api.Configuration

import scala.concurrent.ExecutionContext

case class ErrorMessage(message: String)

final class NotificationConfiguration @Inject()(configuration: Configuration)
  (implicit executionContext: ExecutionContext) {


}

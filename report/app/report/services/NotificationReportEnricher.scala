package report.services

import java.net.URI

import azure.NotificationHubClient
import models.{ExtendedNotificationReport, ExtendedSenderReport, NotificationReport}
import scala.concurrent.ExecutionContext
import scala.PartialFunction._
import scala.concurrent.Future
import scala.util.Try
import cats.syntax.either._

class NotificationReportEnricher(hubClient: NotificationHubClient)(implicit ec: ExecutionContext) {

  def enrich(notificationReport: NotificationReport): Future[ExtendedNotificationReport] = {
    val extended = ExtendedNotificationReport.fromNotificationReport(notificationReport)
    Future.sequence(extended.reports.map(addDebugField)).map { extendedReports =>
      extended.copy(reports = extendedReports)
    }
  }

  private def addDebugField(report: ExtendedSenderReport) = {
    val withDebug = for {
      fullId <- report.sendersId
      id <- extractId(fullId)
    } yield {
      hubClient.notificationDetails(id) map { details =>
        report.copy(debug = details.toOption)
      }
    }
    withDebug.getOrElse(Future.successful(report))
  }

  private def extractId(fullId: String): Option[String] = safeURI(fullId).flatMap { uri =>
    condOpt(uri.getPath.split('/').toList) {
      case "" :: hubName :: "messages" :: hubId :: _ => hubId
    }
  }

  private def safeURI(uri: String) = Try(new URI(uri)).toOption
}

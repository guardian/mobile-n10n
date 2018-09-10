package com.gu.notifications.events

import java.io.{InputStream, OutputStream}
import java.util.concurrent.{ForkJoinPool, TimeUnit}

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.amazonaws.util.IOUtils
import com.gu.notifications.events.model.NotificationReportEvent
import org.apache.logging.log4j.{LogManager, Logger}
import play.api.libs.json.Json

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

object Lambda extends App {

  new Lambda().handleRequest(System.in, System.out, null)
}
case class AttemptedCount(success:Int, failure:Int)
class Lambda(eventConsumer: S3EventProcessor, stage: String)(implicit executionContext: ExecutionContext) extends RequestStreamHandler {
  private val logger: Logger = LogManager.getLogger(classOf[Lambda])

  def this() = this(
    new S3EventProcessorImpl(),
    System.getenv().getOrDefault("Stage", "CODE")
  )(ExecutionContext.fromExecutor(new ForkJoinPool(25)))

  val reportUpdater = new ReportUpdater(stage)

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    try {
      val inputString = try {
        IOUtils.toString(input)
      }
      finally {
        input.close()
      }
      S3Event.jf.reads(Json.parse(inputString)).foreach(e => {
        val events = eventConsumer.process(e)
        val attemptsToUpdateEachEvent = reportUpdater.update(events.aggregations.map { case (k, v) => NotificationReportEvent(k.toString, v) }.toList)
        val countedAttempts = attemptsToUpdateEachEvent.foldRight(Future.successful(AttemptedCount(0, 0))) {
          case (attempt, futureCount) => attempt.transformWith {
            case Success(_) => futureCount.map(lastAttemptCount => lastAttemptCount.copy(success = lastAttemptCount.success + 1))
            case Failure(_) => futureCount.map(lastAttemptCount => lastAttemptCount.copy(failure = lastAttemptCount.failure + 1))
          }
        }
        logger.info(Await.result(countedAttempts, Duration(4, TimeUnit.MINUTES)))
      })
    }
    catch {
      case t: Throwable =>
        logger.warn("Error running lambda", t)
        throw t
    }
    finally {
      output.close()
    }
  }

}
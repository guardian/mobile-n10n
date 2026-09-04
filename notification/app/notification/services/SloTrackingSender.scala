package notification.services

import aws.AWSAsync
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class SloTrackingSender(sqsClient: SqsAsyncClient, queueUrl: String)(implicit ec: ExecutionContext) {
  implicit private val logger: Logger = LoggerFactory.getLogger(this.getClass)
  def sendTrackingMessage(notificationId: UUID): Unit = {
    val sendMessageRequest: SendMessageRequest = SendMessageRequest.builder()
      .queueUrl(queueUrl)
      .messageBody(notificationId.toString)
      .delaySeconds(600) // This message will become visible to queue consumers after 10 minutes
      .build()
    AWSAsync.wrapCompletableFuture(sqsClient.sendMessage(sendMessageRequest)) onComplete {
      case Failure(exception) => logger.error("Send tracking message failed", exception)
      case Success(_) => logger.info(s"Sent SQS SLO tracking message to $queueUrl")
    }
  }
}

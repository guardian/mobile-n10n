package notification.services

import aws.AWSAsync
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model.SendMessageRequest
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class SloTrackingSender(sqsClient: AmazonSQSAsync, queueUrl: String)(implicit ec: ExecutionContext) {
  implicit private val logger: Logger = LoggerFactory.getLogger(this.getClass)
  def sendTrackingMessage(notificationId: UUID): Unit = {
    val sendMessageRequest: SendMessageRequest = new SendMessageRequest()
      .withQueueUrl(queueUrl)
      .withMessageBody(notificationId.toString)
      .withDelaySeconds(600) // This message will become visible to queue consumers after 10 minutes
    AWSAsync.wrapAsyncMethod(sqsClient.sendMessageAsync, sendMessageRequest) onComplete {
      case Failure(exception) => logger.error("Send tracking message failed", exception)
      case Success(_) => logger.info(s"Sent SQS SLO tracking message to $queueUrl")
    }
  }
}

package notification.controllers

import db.RegistrationService
import scala.concurrent.ExecutionContext
import cats.effect.IO
import cats.data.NonEmptyList
import db.Topic
import db.HarvestedToken
import akka.http.scaladsl.model.HttpHeader
import play.api.mvc.ControllerComponents
import play.api.mvc.AbstractController

import org.slf4j.{Logger, LoggerFactory}
import akka.actor.ActorRef
import akka.actor.ActorSystem
import scala.concurrent.duration._

class LoadCache(
  tokensCache: MyActorTask,
  controllerComponents: ControllerComponents
)(implicit ec: ExecutionContext) extends AbstractController(controllerComponents) {

    def loadTokens = Action {

      // Get current size of heap in bytes
      val heapSize: Long = Runtime.getRuntime().totalMemory(); 

      // Get maximum size of heap in bytes. The heap cannot grow beyond this size.// Any attempt will result in an OutOfMemoryException.
      val heapMaxSize: Long = Runtime.getRuntime().maxMemory();

      // Get amount of free memory within the heap in bytes. This size will increase // after garbage collection and decrease as new objects are created.
      val heapFreeSize: Long = Runtime.getRuntime().freeMemory(); 

      Ok(s"Heap Size : ${heapSize/1024/1024} MB \nHeap Max Size : ${heapMaxSize/1024/1024} MB \nHeap Free Size : ${heapFreeSize/1024/1024} MB \nCache: tokens (${tokensCache.cache.length})")

  }
}


class MyActorTask (actorSystem: ActorSystem, registrationService: RegistrationService[IO, fs2.Stream]) (
    implicit executionContext: ExecutionContext
) {
  actorSystem.scheduler.scheduleAtFixedRate(
    initialDelay = 2.minutes,
    interval = 10.minutes
  ) { () => refresh() }

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  var cache: List[HarvestedToken] = List()

  def refresh(): Unit = {
      val breakingTopics = NonEmptyList.of(Topic("breaking/uk"), Topic("breaking/us"), Topic("breaking/au"), Topic("breaking/international"))
      logger.info(s"Query DB started")
      cache = registrationService.findTokens(breakingTopics, None).compile.toList.unsafeRunSync()
      logger.info(s"Query DB ended")
  }
}
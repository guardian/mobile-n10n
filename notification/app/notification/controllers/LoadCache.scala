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
      Ok(s"Cache: tokens ($tokensCache.cache.length)")
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
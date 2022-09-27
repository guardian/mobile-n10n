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

class LoadCache(
  registrationService: RegistrationService[IO, fs2.Stream],
  controllerComponents: ControllerComponents
)(implicit ec: ExecutionContext) extends AbstractController(controllerComponents) {

    var cache: List[HarvestedToken] = List()

    def loadTokens = Action {

      val breakingTopics = NonEmptyList.of(Topic("breaking/uk"), Topic("breaking/us"), Topic("breaking/au"), Topic("breaking/international"))
      cache = registrationService.findTokens(breakingTopics, None).compile.toList.unsafeRunSync()
      Ok(s"Load tokens ($cache.length)")
  }
}

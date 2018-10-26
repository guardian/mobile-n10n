package report.controllers

import authentication.AuthAction
import cats.data.NonEmptyList
import cats.effect.IO
import db.RegistrationService
import models.Topic
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import scala.concurrent.Future

class RegistrationCount(
  registrationService: RegistrationService[IO, fs2.Stream],
  controllerComponents: ControllerComponents,
  authAction: AuthAction
) extends AbstractController(controllerComponents) {

  def forTopics(topics: List[Topic]): Action[AnyContent] = authAction.async { request =>
    topics match {
      case Nil => Future.successful(BadRequest("Couldn't find any valid topic"))
      case firstTopic :: moreTopics =>
        val nonEmptyTopics = NonEmptyList(firstTopic, moreTopics)
        registrationService.countPerPlatformForTopics(nonEmptyTopics.map(topic => db.Topic(topic.toString)))
          .map(result => Ok(Json.toJson(result)))
          .unsafeToFuture()
    }

  }

}

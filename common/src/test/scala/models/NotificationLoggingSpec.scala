package models

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json

class NotificationLoggingSpec extends Specification {
  "Logging custom logs as a string" should {
    "return expected json string" in new CustomJsLoggingScope {
      val message = "test"
      val fields = List(NotificationTypeField("BreakingNews"), NotificationTitleField("title"))
      val jsString = NotificationLogging.customLogsAsJsString(message, fields)

      jsString shouldEqual Json.parse(expected).toString
    }
  }
}

trait CustomJsLoggingScope extends Scope {
  val expected =
    """
      |{
      |  "message" : "test",
      |  "notificationType" : "BreakingNews",
      |  "notificationTitle" : "title"
      |}
""".stripMargin
}


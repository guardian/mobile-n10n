package registration.services.fcm

import com.google.firebase.messaging.FirebaseMessaging
import metrics.DummyMetrics
import models.TopicTypes.Breaking
import models.{Android, Topic}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import registration.services.Configuration

class FcmRegistrarSpec(implicit ee: ExecutionEnv) extends Specification with Mockito {
  "FcmRegistrar.Instance" should {
    "Parse Json with a missing 'rel' attribute" in new FcmRegistrarScope {
      val json =
        """{
          |  "applicationVersion": "0",
          |  "connectDate": "2018-07-31",
          |  "attestStatus": "NOT_ROOTED",
          |  "application": "com.guardian.debug",
          |  "scope": "*",
          |  "authorizedEntity": "232910945129",
          |  "platform": "ANDROID"
          |}
          |""".stripMargin
      val instance = fcmRegistrar.Instance.instanceJF.reads(Json.parse(json)).get

      instance shouldEqual fcmRegistrar.Instance(Nil, Android)
    }
    "Parse Json with a 'rel' attribute" in new FcmRegistrarScope {
      val json =
        """{
          |  "applicationVersion": "0",
          |  "connectDate": "2018-07-31",
          |  "attestStatus": "NOT_ROOTED",
          |  "application": "com.guardian.debug",
          |  "scope": "*",
          |  "authorizedEntity": "232910945129",
          |  "rel": {
          |    "topics": {
          |      "breaking%uk": {
          |        "addDate": "2018-07-31"
          |      }
          |    }
          |  },
          |  "platform": "ANDROID"
          |}""".stripMargin
      val instance = fcmRegistrar.Instance.instanceJF.reads(Json.parse(json)).get

      instance shouldEqual fcmRegistrar.Instance(List(Topic(Breaking, "uk")), Android)
    }
  }

  trait FcmRegistrarScope extends Scope {
    val fcmRegistrar = new FcmRegistrar(
      firebaseMessaging = mock[FirebaseMessaging],
      ws = mock[WSClient],
      configuration = mock[Configuration],
      metrics = DummyMetrics,
      fcmExecutionContext = ee.executionContext
    )
  }
}

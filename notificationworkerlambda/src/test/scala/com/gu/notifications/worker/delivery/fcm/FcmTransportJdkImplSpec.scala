package com.gu.notifications.worker.delivery.fcm

import org.specs2.matcher.Matchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.mock.Mockito
import play.api.libs.json.{Json, JsValue, JsSuccess, JsError}
import java.net.http.HttpResponse
import com.google.auth.oauth2.GoogleCredentials
import com.google.api.client.json.JsonFactory
import com.gu.notifications.worker.delivery.fcm.models.payload.FcmErrorPayload

class FcmTransporttJdkImplSpec extends Specification with Matchers with Mockito {

  "FcmTransporttJdkImpl" should {
    "parse an invalid token error response correctly from from Firebase API" in new FcmTransportTestScope {
      val responseBody = 
        """
        |{
        |  "error": {
        |    "code": 404,
        |    "message": "The registration token is not a valid FCM registration token",
        |    "status": "NOT_FOUND",
        |    "details": [
        |      {
        |        "@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError",
        |        "errorCode": "UNREGISTERED"
        |      }
        |    ]
        |  }
        |}""".stripMargin

      val response = mock[HttpResponse[String]]
      response.statusCode() returns 404
      response.body() returns responseBody
      fcmTransport.handleResponse(response) must beFailedTry(
        InvalidTokenException(FcmErrorPayload(404, "The registration token is not a valid FCM registration token", "NOT_FOUND", Some("UNREGISTERED")))
      )
   }

    "parse an unexpected error response with general details field from Firebase API" in new FcmTransportTestScope {
      val responseBody = 
        """
        |{
        |  "error": {
        |    "code": 404,
        |    "message": "The registration token is not a valid FCM registration token",
        |    "status": "NOT_FOUND",
        |    "details": [
        |    {
        |      "@type": "type.googleapis.com/google.rpc.BadRequest",
        |      "fieldViolations": [
        |        {
        |          "field": "message.data[0].value",
        |          "description": "Invalid value at 'message.data[0].value' (TYPE_STRING), 12"
        |        }
        |      ]
        |    }
        |    ]
        |  }
        |}""".stripMargin

      val response = mock[HttpResponse[String]]
      response.statusCode() returns 404
      response.body() returns responseBody
      fcmTransport.handleResponse(response) must beFailedTry(
        UnknownException(FcmErrorPayload(404, "The registration token is not a valid FCM registration token", "NOT_FOUND", None))
      )
   }

    "parse an internal error object from the error response without details field from Firebase API" in new FcmTransportTestScope {
      val responseBody = 
        """
        |{
        |  "error": {
        |    "code": 500,
        |    "message": "The registration token is not a valid FCM registration token",
        |    "status": "INTERNAL"
        |  }
        |}""".stripMargin

      val response = mock[HttpResponse[String]]
      response.statusCode() returns 500
      response.body() returns responseBody
      fcmTransport.handleResponse(response) must beFailedTry(
        FcmServerException(FcmErrorPayload(500, "The registration token is not a valid FCM registration token", "INTERNAL", None))
      )
    }

    "handle unsupported error response from Firebase API gracefully" in new FcmTransportTestScope {
      val responseBody = 
        """
        | it is a invalid JSON 
        |  "error": {
        |    "code": 500,
        |    }{}{}{}{}
        |  }
        |}""".stripMargin

      val response = mock[HttpResponse[String]]
      response.statusCode() returns 505
      response.body() returns responseBody
      fcmTransport.handleResponse(response) must beFailedTry
    }
  }
}

trait FcmTransportTestScope extends Scope {

  val mockCredential = Mockito.mock[GoogleCredentials]
  val mockJsonFactory = Mockito.mock[JsonFactory]
  val fcmTransport: FcmTransportJdkImpl = new FcmTransportJdkImpl(mockCredential, "TEST_URL", mockJsonFactory, 10, 10)
}

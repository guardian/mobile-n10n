package gu.msnotifications

import java.net.URI

import notificationhubs.SasTokenGenerator

case class NotificationHub(namespace: String, notificationHub: String, secretKeyName: String, secretKey: String) {

  def authorizationHeader(uri: URI) = SasTokenGenerator.generateSasToken(secretKeyName, secretKey, uri)

  def notificationsHubUrl = s"""https://$namespace.servicebus.windows.net/$notificationHub"""

  case object PostRegistrations {
    def url = s"""$notificationsHubUrl/registrations/?api-version=2015-01"""
    def authHeader = authorizationHeader(new URI(url))
  }

  case class UpdateRegistration(registrationId: RegistrationId) {
    // todo sanitise input here
    def url = s"""$notificationsHubUrl/registration/${registrationId.registrationId}?api-version=2015-01"""
    def authHeader = authorizationHeader(new URI(url))
  }

  case object ListRegistrations {
    def url = s"""$notificationsHubUrl/registrations/?api-version=2015-01"""
    def authHeader = authorizationHeader(new URI(url))
  }

  case class ResponseParser(xml: scala.xml.Elem) {

    def error: Option[(Int, String)] = {
      for {
        code <- xml \ "Code"
        detail <- xml \ "Detail"
      } yield code.text.toInt -> detail.text
    }.headOption

  }

  case class RegistrationResponseParser(xml: scala.xml.Elem) {

    def error = ResponseParser(xml).error

    def registrationId: Option[String] = {
      (xml \\ "RegistrationId").map(_.text).headOption
    }

  }

}
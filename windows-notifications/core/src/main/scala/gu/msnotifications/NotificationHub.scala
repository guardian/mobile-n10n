package gu.msnotifications

import java.net.URI

import notificationhubs.SasTokenGenerator

case class NotificationHub(namespace: String, notificationHub: String, secretKeyName: String, secretKey: String) {

  def authorizationHeader(uri: URI) = SasTokenGenerator.generateSasToken(secretKeyName, secretKey, uri)

  def notificationsHubUrl = s"""https://$namespace.servicebus.windows.net/$notificationHub"""

  def registrationsPostUrl = s"""$notificationsHubUrl/registrations/?api-version=2015-01"""

  def registrationsAuthorizationHeader = authorizationHeader(new URI(registrationsPostUrl))

  /** Need to sanitise the input here **/
  def registrationUrl(registrationId: String) = s"""$notificationsHubUrl/registration/$registrationId"""

  def registrationAuthorizationHeader(registrationId: String) = authorizationHeader(new URI(registrationUrl(registrationId)))

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
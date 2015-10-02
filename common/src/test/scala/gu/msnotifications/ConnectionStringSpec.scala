package gu.msnotifications

import org.specs2.mutable.Specification

class ConnectionStringSpec extends Specification {
  "Connection string" should {

    "be parsed correctly" in {
      val (namespace, name, keyName, key) = ("a-ns", "b", "keyName", "key/ad=")
      val endpoint = s"""Endpoint=sb://$namespace.servicebus.windows.net/;SharedAccessKeyName=$keyName;SharedAccessKey=$key"""
      val notificationHub = ConnectionSettings.fromString(endpoint).map(_.buildNotificationHub(name)).getOrElse(throw new Exception("error"))
      notificationHub.secretKeyName mustEqual keyName
      notificationHub.secretKey mustEqual key
    }
  }
}

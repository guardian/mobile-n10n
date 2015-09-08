package gu.msnotifications

import org.scalatest.{Matchers, WordSpec}

class ConnectionStringSpec extends WordSpec with Matchers {
  "Connection string" must {
    "be parsed correctly" in {
      val (namespace, name, keyName, key) = ("a-ns", "b", "keyName", "key/ad=")
      val endpoint = s"""Endpoint=sb://$namespace.servicebus.windows.net/;SharedAccessKeyName=$keyName;SharedAccessKey=$key"""
      val notificationHub = ConnectionString(endpoint).buildNotificationHub(name).get
      notificationHub.namespace shouldBe namespace
      notificationHub.notificationHub shouldBe name
      notificationHub.secretKeyName shouldBe keyName
      notificationHub.secretKey shouldBe key
    }
  }
}

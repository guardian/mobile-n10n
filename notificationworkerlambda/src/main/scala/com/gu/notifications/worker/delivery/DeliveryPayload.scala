package com.gu.notifications.worker.delivery

import com.google.firebase.messaging.AndroidConfig

sealed trait DeliveryPayload
case class ApnsPayload(jsonString: String, ttl: Option[Long] = None, collapseId: Option[String]) extends DeliveryPayload
case class FcmPayload(androidConfig: AndroidConfig) extends DeliveryPayload

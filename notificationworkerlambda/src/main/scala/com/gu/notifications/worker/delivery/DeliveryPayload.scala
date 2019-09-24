package com.gu.notifications.worker.delivery

import com.google.firebase.messaging.AndroidConfig
import com.turo.pushy.apns.PushType

sealed trait DeliveryPayload
case class ApnsPayload(jsonString: String, ttl: Option[Long] = None, collapseId: Option[String], pushType: PushType) extends DeliveryPayload
case class FcmPayload(androidConfig: AndroidConfig) extends DeliveryPayload

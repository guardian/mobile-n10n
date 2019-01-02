package com.gu.notifications.worker.delivery

import com.google.firebase.messaging.AndroidConfig

sealed trait DeliveryPayload
class ApnsPayload(val jsonString: String, val ttl: Option[Long] = None) extends DeliveryPayload
class FcmPayload(val androidConfig: AndroidConfig) extends DeliveryPayload

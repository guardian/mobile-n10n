package com.gu.notifications.worker.delivery.apns

import cats.effect.{Async, Concurrent}
import com.gu.notifications.worker.delivery.{ApnsDeliverySuccess, ApnsPayload, DeliveryService}
import db.RegistrationService
import fs2.Stream

import scala.concurrent.ExecutionContextExecutor

class Apns[F[_]](registrationService: RegistrationService[F, Stream], client: ApnsClient)
  (implicit ece: ExecutionContextExecutor, contextShift: Concurrent[F], F: Async[F])
  extends DeliveryService[F, ApnsPayload, ApnsDeliverySuccess, ApnsClient](registrationService, client)


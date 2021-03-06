package com.gu.notifications.worker.delivery.apns

import cats.effect.{Async, Concurrent, Timer}
import com.gu.notifications.worker.delivery.DeliveryServiceImpl

import scala.concurrent.ExecutionContextExecutor

class Apns[F[_]](client: ApnsClient)
  (implicit ece: ExecutionContextExecutor, contextShift: Concurrent[F], F: Async[F], T: Timer[F])
  extends DeliveryServiceImpl[F, ApnsClient](client)


package com.gu.notifications.worker.delivery.fcm

import cats.effect.{Async, Concurrent, Timer}
import com.gu.notifications.worker.delivery.DeliveryServiceImpl

import scala.concurrent.ExecutionContextExecutor

class Fcm[F[_]](client: FcmClient)
  (implicit ece: ExecutionContextExecutor, contextShift: Concurrent[F], F: Async[F], T: Timer[F])
  extends DeliveryServiceImpl[F, FcmClient](client)


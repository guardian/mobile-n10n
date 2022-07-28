package com.gu.notifications.workerlambda.delivery.apns

import cats.effect.{Async, Concurrent, Timer}
import com.gu.notifications.workerlambda.delivery.DeliveryServiceImpl

import scala.concurrent.ExecutionContextExecutor

class Apns[F[_]](client: ApnsClient)
  (implicit ece: ExecutionContextExecutor, contextShift: Concurrent[F], F: Async[F], T: Timer[F])
  extends DeliveryServiceImpl[F, ApnsClient](client)


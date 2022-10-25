package com.gu.notifications.ec2worker

import cats.effect.{ContextShift, IO, Timer}
import com.gu.notifications.worker.cleaning.CleaningClientImpl
import com.gu.notifications.worker.delivery.apns.{Apns, ApnsClient}
import com.gu.notifications.worker.utils.{Cloudwatch, CloudwatchImpl}

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import _root_.models.{Android, AndroidBeta, AndroidEdition, Ios, IosEdition, Platform}
import com.gu.notifications.worker.SenderRequestHandler
import com.gu.notifications.worker.ApnsWorkerConfiguration
import com.typesafe.config.Config
import com.gu.notifications.worker.IOSSender

class Ec2IOSSender(appConfig: Config, iosPlatform: Platform) extends IOSSender {

  override val config: ApnsWorkerConfiguration = Configuration.fetchApns(appConfig, iosPlatform)

}

package com.gu.notifications.events

import java.util

import scala.util.Try

class EnvDependencies {
  private val env: util.Map[String, String] = System.getenv()

  private def getNeededKey(key: String): String =
    Try{Option(env.get(key))}.toOption.flatten.map(_.trim) match {
      case Some(value) if value.nonEmpty => value
      case _ => throw new RuntimeException(s"Missing environment key $key")
    }


  val athenaOutputLocation: String = getNeededKey("AthenaOutputLocation")
  val athenaDatabase: String = getNeededKey("AthenaDatabase")
  val stage = env.getOrDefault("Stage", "CODE")
  val ingestLocation: String = getNeededKey("IngestLocation")
}

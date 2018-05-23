package com.gu.notificationshards.lambda

import java.io.{InputStream, OutputStream}
import java.nio.charset.StandardCharsets.UTF_8

import org.apache.commons.io.IOUtils
import org.apache.logging.log4j.{LogManager, Logger}

import scala.util.{Failure, Try}


trait Lambda {
  def execute(input: InputStream, output: OutputStream): Unit
}

class LambdaImpl(function: String => String) extends Lambda {
  private val logger: Logger = LogManager.getLogger(classOf[LambdaImpl])

  def execute(input: InputStream, output: OutputStream): Unit = {
    Try {
      try {
        objectReadAndClose(input).map(inputString => {
          val outputString = function(inputString)
          output.write(outputString.getBytes(UTF_8))
        })

      }
      finally output.close()
    }.flatten match {
      case Failure(t) => {
        logger.error(s"Unable to read input", t)
        throw t
      }
      case _ => ()
    }
  }

  private def objectReadAndClose(input: InputStream): Try[String] = {
    Try {
      try {
        new String(IOUtils.toByteArray(input), UTF_8)
      } finally {
        input.close()
      }
    }
  }
}

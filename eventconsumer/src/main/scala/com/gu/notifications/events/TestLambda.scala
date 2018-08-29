package com.gu.notifications.events

import java.io.{InputStream, OutputStream}

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.amazonaws.util.IOUtils


class TestLambda extends RequestStreamHandler{
  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    try {
      println(IOUtils.toString(input))
    }
    finally {
      input.close()
    }

  }
}

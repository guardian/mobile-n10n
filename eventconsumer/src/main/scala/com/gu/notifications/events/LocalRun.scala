package com.gu.notifications.events

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets

import com.gu.notifications.events.aws.AwsClient

object LocalRun extends App {
  if (args.size < 1) {
    System.out.println("No argument")
  }
  else {
    args(0) match {
      case "athena" => {
        new AthenaLambda().handleRequest()
        AwsClient.dynamoDbClient.shutdown()
      }
      case "sqs" => {
        val is = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8))
        val os = new ByteArrayOutputStream()
        new SqsLambda().handleRequest(is, os, null)
        System.out.println(new String(os.toByteArray, StandardCharsets.UTF_8))
      }
    }
  }


}

package com.gu.notificationshards.external

import java.util.concurrent.ForkJoinPool

import scala.concurrent.ExecutionContext

object Parallelism {
  val largeGlobalExecutionContext: ExecutionContext = ExecutionContext.fromExecutor(new ForkJoinPool(5)) // in a lambda default is really low, like 2/3.
}

package com.gu.notifications.worker.delivery.fcm.oktransport

import com.google.api.client.http.{HttpTransport, LowLevelHttpRequest}
import okhttp3.{Dns, OkHttpClient}

import scala.collection.JavaConverters._
import scala.util.Random


class OkGoogleHttpTransport extends HttpTransport {
  private val okHttpClient = new OkHttpClient.Builder()
    .dns((hostname: String) => Random.shuffle(Dns.SYSTEM.lookup(hostname).asScala).asJava)
    .followRedirects(false)
    .build()

  override def shutdown: Unit = {
    okHttpClient.dispatcher().executorService().shutdown()
    okHttpClient.connectionPool().evictAll()
    Option(okHttpClient.cache()).foreach(_.close())

  }


  override def buildRequest(method: String, url: String): LowLevelHttpRequest = new OkGoogleHttpRequest(okHttpClient, url, method)
}

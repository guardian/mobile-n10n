package com.gu.mobile.notifications.football.lib

import java.io.IOException

import com.gu.Logging
import pa._

import scala.concurrent.{ExecutionContext, Future, Promise}
import okhttp3.{Call, Callback, OkHttpClient, Request}
import org.joda.time.DateTime

trait OkHttp extends pa.Http with Logging {

  implicit val ec: ExecutionContext

  val httpClient = new OkHttpClient

  def apiKey: String

  def GET(urlString: String): Future[Response] = {

    val promise = Promise[Response]

    logger.info("Http GET " + urlString.replaceAll(apiKey, "<api-key>"))
    val httpRequest = new Request.Builder().url(urlString).build()
    httpClient.newCall(httpRequest).enqueue(new Callback {
      override def onFailure(call: Call, e: IOException): Unit = promise.failure(e)
      override def onResponse(call: Call, response: okhttp3.Response): Unit = {
        promise.success(Response(response.code(), response.body().string, response.message()))
      }
    })

    promise.future
  }
}

class PaFootballClient(override val apiKey: String, apiBase: String) extends PaClient with OkHttp {

  override implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  override lazy val base = apiBase

  override protected def get(suffix: String)(implicit context: ExecutionContext): Future[String] = super.get(suffix)(context)

  def aroundToday: Future[List[MatchDay]] = {
    val today = DateTime.now.toLocalDate
    val yesterday = today.minusDays(1)
    val tomorrow = today.plusDays(1)

    val days = List(yesterday, today, tomorrow)

    Future.reduce(days.map { day => matchDay(day).recover { case _ => List.empty } })(_ ++ _)
  }

  def eventsForMatch(matchDay: MatchDay, syntheticMatchEventGenerator: SyntheticMatchEventGenerator)(implicit ec: ExecutionContext): Future[(MatchDay, List[MatchEvent])] =
    for {
      events <- matchEvents(matchDay.id).map(_.toList.flatMap(_.events))
    } yield {
      (matchDay, syntheticMatchEventGenerator.generate(events, matchDay.id, matchDay))
    }
}

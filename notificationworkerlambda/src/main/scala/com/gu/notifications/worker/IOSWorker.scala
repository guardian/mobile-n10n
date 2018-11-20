package com.gu.notifications.worker

import cats.effect.IO
import com.gu.notifications.worker.delivery.apns.{Apns, ApnsClient}
import db.{DatabaseConfig, RegistrationService}
import doobie.util.transactor.Transactor
import _root_.models.iOS
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.gu.notifications.worker.cleaning.CleaningClient
import scala.collection.JavaConverters._

class IOSWorker extends WorkerRequestHandler[ApnsClient] {
  val platform = iOS
  val config: ApnsWorkerConfiguration = Configuration.fetchApns()
  val transactor: Transactor[IO] = DatabaseConfig.transactor[IO](config.jdbcConfig)
  val registrationService = RegistrationService(transactor)
  val cleaningClient = new CleaningClient(config.sqsUrl)

  override val deliveryService: IO[Apns[IO]] =
    ApnsClient(config.apnsConfig).fold(e => IO.raiseError(e), c => IO.delay(new Apns(registrationService, c)))
}

object Run {
  val iOSWorker = new IOSWorker

  def main(args: Array[String]): Unit = {
    val sqsMessage = new SQSMessage()
    sqsMessage.setBody("{\"notification\":{\"id\":\"35cdc7eb-da53-4677-adbe-d7680c4e3db4\",\"type\":\"football-match-status\",\"title\":\"Goal!\",\"message\":\"Man Utd 1-0 Leicester (1st)\\nPaul Pogba 3min (pen)\",\"sender\":\"mobile-notifications-football-lambda\",\"awayTeamName\":\"Leicester\",\"awayTeamScore\":0,\"awayTeamMessage\":\" \",\"awayTeamId\":\"29\",\"homeTeamName\":\"Man Utd\",\"homeTeamScore\":1,\"homeTeamMessage\":\"Paul Pogba 3' pen\",\"homeTeamId\":\"12\",\"competitionName\":\"Premier League 18/19\",\"venue\":\"Old Trafford\",\"matchId\":\"4089149\",\"matchInfoUri\":\"https://mobile.guardianapis.com/sport/football/matches/4089149\",\"articleUri\":\"https://mobile.guardianapis.com/items/football/live/2018/aug/10/manchester-united-v-leicester-city-premier-league-live\",\"importance\":\"Major\",\"topic\":[{\"type\":\"football-team\",\"name\":\"12\"},{\"type\":\"football-team\",\"name\":\"29\"},{\"type\":\"football-match\",\"name\":\"4089149\"}],\"matchStatus\":\"1st\",\"eventId\":\"70bf247f-6c31-3f80-92b8-4788921e3580\",\"debug\":false},\"range\":{\"start\":-32768,\"end\":32767}}")
    val sqsEvent = new SQSEvent()
    sqsEvent.setRecords(List(sqsMessage).asJava)
    iOSWorker.handleRequest(sqsEvent, null)
  }
}
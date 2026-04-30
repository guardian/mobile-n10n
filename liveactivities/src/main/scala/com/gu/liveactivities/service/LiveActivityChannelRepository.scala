package com.gu.liveactivities.service

import cats.data.NonEmptyList
import com.gu.liveactivities.models.{LiveActivityData, LiveActivityDataException, LiveActivityMapping, RepositoryException}
import com.gu.liveactivities.util.DateTimeHelper.dateTimeToString
import com.gu.liveactivities.util.Logging
import org.scanamo.syntax._
import org.scanamo.update.UpdateExpression
import org.scanamo.{ConditionNotMet, DynamoReadError, ScanamoAsync, Table}
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

import java.time.ZonedDateTime
import scala.concurrent.{ExecutionContext, Future}

trait ChannelMappingsRepository {

  def containMapping(id: String): Future[Option[String]]

  def createMapping(
    id: String,
    channelId: String,
    eventData: Option[LiveActivityData]): Future[Unit]

  def getMappingById(id: String): Future[LiveActivityMapping]
  
  def updateMappingActiveChannel(id: String, isActive: Boolean): Future[Unit]

  def updateMappingLive(id: String, isLive: Boolean): Future[Unit]

  def updateMappingLastEvent(id: String, lastEventId: Option[String], lastEventUpdate: Option[ZonedDateTime]): Future[Unit]
  
  def deleteMappingById(id: String): Future[Unit]
}

class LiveActivityChannelRepository(client: DynamoDbAsyncClient, tableName: String)(
    implicit ec: ExecutionContext
) extends ChannelMappingsRepository with Logging {

  private val scanamo = ScanamoAsync(client)
  private val table = Table[LiveActivityMapping](tableName)
  private val idKeyName = "id"

  override def containMapping(id: String): Future[Option[String]] =
    scanamo.exec {
        table.get(idKeyName === id)
      }
      .map {
        case Some(Right(mapping)) => Some(mapping.channelId)
        case None => None
        case Some(Left(ex)) =>
          val errorMsg = s"Error checking if live activity mapping exists for id $id - ${DynamoReadError.describe(ex)}"
          logger.error(errorMsg)
          throw new RepositoryException(errorMsg)
      }.recover(handleErrors("reading from DynamoDB", id))

  override def createMapping(
    id: String,
    channelId: String,
    eventData: Option[LiveActivityData],
  ): Future[Unit] = {
    val createdAt = ZonedDateTime.now()
    val newItem = new LiveActivityMapping(
      id = id, 
      channelId = channelId, 
      isChannelActive = true, 
      isLive = true, 
      data = eventData,
      lastEventId = None,
      lastEventAt = None,
      createdAt = createdAt,
      lastModifiedAt = createdAt
    )
    scanamo.exec {
        table.when(attributeNotExists(idKeyName)).put(newItem)
      }
      .map {
        case Right(_) => ()
        case Left(ConditionNotMet(ex)) =>
          val errorMsg = s"Live activity mapping for id $id already exists"
          logger.error(errorMsg)
          throw new RepositoryException(ex, errorMsg)
        case Left(ex: DynamoReadError) =>
          val errorMsg = s"Error saving live activity mapping for id $id - ${DynamoReadError.describe(ex)}"
          logger.error(errorMsg)
          throw new RepositoryException(errorMsg)
      }.recover(handleErrors("writing to DynamoDB", id))
  }

  private def updateMappingById(
      id: String, 
      isChannelActive: Option[Boolean] = None, 
      isLive: Option[Boolean] = None, 
      lastEventId: Option[String] = None, 
      lastEventAt: Option[ZonedDateTime] = None
  ): Future[Unit] = {
  val updates: List[UpdateExpression] = List(
      isChannelActive.map(c => set("isChannelActive", c)),
      isLive.map(l => set("isLive", l)),
      lastEventId.map(eventId => set("lastEventId", eventId)),
      lastEventAt.map(eventAt => set("lastEventAt", dateTimeToString(eventAt))),
    ).flatten

    NonEmptyList.fromList(updates) match {
      case None =>
        logger.warn(s"No fields to update for live activity mapping with id $id")
        Future.successful(())
      case Some(ups) =>
        val result = scanamo.exec {
          val modifiedAt = ZonedDateTime.now()
          val upsWithLastModified = set("lastModifiedAt", dateTimeToString(modifiedAt)) :: ups
          table.update(idKeyName === id, upsWithLastModified.reduce[UpdateExpression](_ and _))
        }

        result.flatMap {
          case Right(_) => Future.successful(())
          case Left(ex) =>
            val errorMsg = s"Error updating live activity mapping for id $id - ${DynamoReadError.describe(ex)}"
            logger.error(errorMsg)
            Future.failed(new RepositoryException(errorMsg))
        }.recover(handleErrors("updating mapping in DynamoDB", id))
    }
  }

  override def updateMappingActiveChannel(id: String, isActive: Boolean): Future[Unit] = {
    updateMappingById(id, isChannelActive = Some(isActive), isLive = Some(isActive)) // defaults to isLive true on mapping creation
  }

  override def updateMappingLive(id: String, isLive: Boolean): Future[Unit] = {
    updateMappingById(id, isLive = Some(isLive))
  }

  override def updateMappingLastEvent(id: String, lastEventId: Option[String], lastEventAt: Option[ZonedDateTime]): Future[Unit] = {
    updateMappingById(id, lastEventId = lastEventId, lastEventAt = lastEventAt)
  }

  override def getMappingById(
      id: String
  ): Future[LiveActivityMapping] = {
    scanamo.exec {
        table.consistently.get(idKeyName === id)
      }
      .flatMap {
        case Some(Right(result)) => Future.successful(result)
        case None => Future.failed(new LiveActivityDataException(id, "Live Activity mapping not found"))
        case Some(Left(ex)) =>
          val errorMsg = s"Error getting live activity mapping for id $id - ${DynamoReadError.describe(ex)}"
          logger.error(errorMsg)
          Future.failed(new RepositoryException(errorMsg))
      }.recover(handleErrors("reading mapping from DynamoDB", id))
  }

  override def deleteMappingById(
      id: String
  ): Future[Unit] = {
    scanamo.exec { 
      table.delete(idKeyName === id)
    }
  }

  private def handleErrors[T](operation: String, id: String): PartialFunction[Throwable, T] = {
    case ex: RepositoryException =>
      throw ex
    case ex: Exception =>
      val msg = s"Error during $operation for id $id: ${ex.getMessage}"
      logger.error(msg)
      throw new RepositoryException(msg)
  }
}

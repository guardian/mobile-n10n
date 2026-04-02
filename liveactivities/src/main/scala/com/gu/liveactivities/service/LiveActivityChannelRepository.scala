package com.gu.liveactivities.service

import cats.syntax.all._
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json._
import tracking.Repository.RepositoryResult
import tracking.RepositoryError

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import com.gu.liveactivities.models.{LiveActivityData, LiveActivityMapping, RepositoryException, LiveActivityDataException}
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import com.gu.liveactivities.util.Logging
import com.gu.liveactivities.util.FutureUtils._
import scala.util.Failure
import com.gu.liveactivities.util.DynamoJsonConversions.toAttributeMap
import com.gu.liveactivities.util.DynamoJsonConversions.fromAttributeMap
import com.gu.liveactivities
import com.gu.liveactivities.util.DateTimeHelper.{dateTimeFromString, dateTimeToString}
import org.scanamo.{ScanamoAsync, Table, DynamoReadError, ScanamoError, ConditionNotMet}
import org.scanamo.syntax._
import org.scanamo.update.UpdateExpression
import cats.data.NonEmptyList

trait ChannelMappingsRepository {

  def containMapping(id: String): Future[Boolean]

  def createMapping(    
    id: String,
    channelId: String,
    eventData: Option[LiveActivityData],
    competitionId: Option[String]): Future[Unit]

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

  override def containMapping(id: String): Future[Boolean] =
    scanamo.exec { 
      table.get(idKeyName === id)
    }
    .flatMap(_ match {
      case Some(Right(_)) => Future.successful(true)
      case None => Future.successful(false)
      case Some(Left(ex)) => {
        val errorMsg = s"Error checking if live activity mapping exists for id $id - ${DynamoReadError.describe(ex)}"
        logger.error(errorMsg)
        Future.failed(new RepositoryException(errorMsg))
      }
    })

  override def createMapping(
    id: String,
    channelId: String,
    eventData: Option[LiveActivityData],
    competitionId: Option[String],
  ): Future[Unit] = {
    val createdAt = ZonedDateTime.now()
    val newItem = new LiveActivityMapping(
      id = id, 
      channelId = channelId, 
      isChannelActive = true, 
      isLive = true, 
      data = eventData, 
      competitionId = competitionId,
      lastEventId = None,
      lastEventAt = None,
      createdAt = createdAt,
      lastModifiedAt = createdAt
    )
    scanamo.exec { 
      table.when(attributeNotExists(idKeyName)).put(newItem)
    }
    .flatMap(_ match {
      case Right(_) => Future.successful(())
      case Left(ConditionNotMet(ex)) => {
        val errorMsg = s"Live activity mapping for id $id already exists"
        logger.error(errorMsg)
        Future.failed(new RepositoryException(ex, errorMsg))
      }
      case Left(ex: DynamoReadError) => {
        val errorMsg = s"Error saving live activity mapping for id $id - ${DynamoReadError.describe(ex)}"
        logger.error(errorMsg)
        Future.failed(new RepositoryException(errorMsg))
      }
    })
  }

  private def updateMappingById(
      id: String, 
      isChannelActive: Option[Boolean] = None, 
      isLive: Option[Boolean] = None, 
      lastEventId: Option[String] = None, 
      lastEventAt: Option[ZonedDateTime] = None
  ): Future[Unit] = {
    if (Seq(isChannelActive, isLive, lastEventId, lastEventAt).forall(_.isEmpty)) {
      logger.warn(s"No fields to update for live activity mapping with id $id")
      Future.successful(())
    } else {

      val modifiedAt = ZonedDateTime.now()
      val updates: List[UpdateExpression] = List(
          isChannelActive.map(c => set("isChannelActive", c)),
          isLive.map(l => set("isLive", l)),
          lastEventId.map(eventId => set("lastEventId", eventId)),
          lastEventAt.map(eventAt => set("lastEventAt", dateTimeToString(eventAt))),
          Some(set("lastModifiedAt", dateTimeToString(modifiedAt)))
        ).flatten
      val result = scanamo.exec {
        NonEmptyList.fromList(updates).map(ups =>
          table.update(idKeyName === id, ups.reduce[UpdateExpression](_ and _))
        ).sequence
      }
      result.flatMap(_ match {
        case Some(Right(_)) => Future.successful(())
        case None => {
          val errorMsg = s"Live activity mapping for id $id not found for update"
          logger.error(errorMsg)
          Future.failed(new LiveActivityDataException(id, errorMsg))
        }
        case Some(Left(ex)) => {
          val errorMsg = s"Error updating live activity mapping for id $id - ${DynamoReadError.describe(ex)}"
          logger.error(errorMsg)
          Future.failed(new RepositoryException(errorMsg))
        }
      })
    }
  }

  override def updateMappingActiveChannel(id: String, isActive: Boolean): Future[Unit] = {
    updateMappingById(id, isChannelActive = Some(isActive), isLive = Some(isActive))
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
    .flatMap(_ match {
      case Some(Right(result)) => Future.successful(result)
      case None => Future.failed(new LiveActivityDataException(id, "Live Activity mapping not found"))
      case Some(Left(ex)) => {
        val errorMsg = s"Error getting live activity mapping for id $id - ${DynamoReadError.describe(ex)}"
        logger.error(errorMsg)
        Future.failed(new RepositoryException(errorMsg))
      }
    })
  }

  override def deleteMappingById(
      id: String
  ): Future[Unit] = {
    scanamo.exec { 
      table.delete(idKeyName === id)
    }
  }

}

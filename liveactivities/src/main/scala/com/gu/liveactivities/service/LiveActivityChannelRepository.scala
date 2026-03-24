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
import com.gu.liveactivities.models.LiveActivityMapping
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import com.gu.liveactivities.util.Logging
import com.gu.liveactivities.util.FutureUtils._
import scala.util.Failure
import com.gu.liveactivities.util.DynamoJsonConversions.toAttributeMap
import com.gu.liveactivities.util.DynamoJsonConversions.fromAttributeMap

class ChannelMappingDataException(id: String, message: String) extends Exception(s"Channel mapping data error for id $id: $message")

class RepositoryException(cause: Throwable, message: String) extends Exception(message, cause)

trait ChannelMappingsRepository {

  type Result[T] = Either[Exception, T]

  def containMapping(id: String): Future[Result[Boolean]] 

  def createMapping(mapping: LiveActivityMapping): Future[Result[Unit]]

  def getMappingById(id: String): Future[Result[LiveActivityMapping]]
  
  def updateMappingActiveChannel(id: String, isActive: Boolean): Future[Result[Unit]]

  def updateMappingLiveEvent(id: String, isLive: Boolean): Future[Result[Unit]]

  def updateMappingLastEvent(id: String, lastEventId: String, lastEventUpdate: ZonedDateTime): Future[Result[Unit]]
  
  def deleteMappingById(id: String): Future[Result[Unit]]
}

class LiveActivityChannelRepository(client: DynamoDbAsyncClient, tableName: String)(
    implicit ec: ExecutionContext
) extends ChannelMappingsRepository with Logging {

  private val idField = "id"
  private val createdAtKeyName = "createdAt"
  private val lastModifiedAtKeyName = "lastModifiedAt"
  private val iso8601formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZZ")

  def createdAtAttr(t: ZonedDateTime) = Map(
    createdAtKeyName -> AttributeValue.builder().s(t.format(iso8601formatter)).build(),
  )

  def lastModifiedAtAttr(t: ZonedDateTime) = Map(
    lastModifiedAtKeyName -> AttributeValue.builder().s(t.format(iso8601formatter)).build(),
  )

  def containMapping(id: String): Future[Result[Boolean]] = {
    val keyToGet = Map(idField -> AttributeValue.builder().s(id).build())
    val request = GetItemRequest
      .builder()
      .tableName(tableName)
      .key(keyToGet.asJava)
      .build();
    client
      .getItem(request)
      .toScala
      .map(resp => Right(resp.hasItem()))
      .recover { case ex: Exception =>
        logger.error("Error checking if live activity mapping exists", ex)
        Left(new RepositoryException(ex, "Error checking if live activity mapping exists"))
      }
  }

  override def createMapping(
      mapping: LiveActivityMapping
  ): Future[Result[Unit]] = {
    val createdAt = ZonedDateTime.now()
    val putItemRequest =
      PutItemRequest.builder()
        .tableName(tableName)
        .item((toAttributeMap(mapping) ++ createdAtAttr(createdAt) ++ lastModifiedAtAttr(
          createdAt,
        )).asJava)
        .conditionExpression(s"attribute_not_exists($idField)")
        .build()
    client
      .putItem(putItemRequest)
      .toScala
      .map(_ => Right(()))
      .recover { case ex: Exception =>
        logger.error("Error saving live activity mapping", ex)
        Left(new RepositoryException(ex, "Error saving live activity mapping"))
      }
  }

  private def updateMappingById(
      id: String, 
      isChannelActive: Option[Boolean] = None, 
      isEventLive: Option[Boolean] = None, 
      lastEventId: Option[String] = None, 
      lastEventUpdate: Option[ZonedDateTime] = None
  ): Future[Result[Unit]] = {
    val itemKey = Map(idField-> AttributeValue.fromS(id))
    val modifiedAt = ZonedDateTime.now()
    val updateValues = Map(
      "isChannelActive" -> isChannelActive.map {v =>
        AttributeValueUpdate
          .builder()
          .action(AttributeAction.PUT)
          .value(AttributeValue.fromBool(v))
          .build()
      },
      "isEventLive" -> isEventLive.map {v =>
        AttributeValueUpdate
          .builder()
          .action(AttributeAction.PUT)
          .value(AttributeValue.fromBool(v))
          .build()
      },
      "lastEventId" -> lastEventId.map { v =>
        AttributeValueUpdate
          .builder()
          .action(AttributeAction.PUT)
          .value(AttributeValue.fromS(v))
          .build()
      },
      "lastEventUpdate" -> lastEventUpdate.map { v =>
        AttributeValueUpdate
          .builder()          
          .action(AttributeAction.PUT)
          .value(AttributeValue.fromS(v.format(iso8601formatter)))
          .build()
      },
      lastModifiedAtKeyName -> Some(
        AttributeValueUpdate
          .builder()
          .value(AttributeValue.fromS(modifiedAt.format(iso8601formatter)))
          .action(AttributeAction.PUT)
          .build()),
      ).collect { case (k, Some(v)) => k -> v }
    val request =
      UpdateItemRequest.builder()
        .tableName(tableName)
        .key(itemKey.asJava)
        .attributeUpdates(updateValues.asJava)
        .build()
      client
      .updateItem(request)
      .toScala
      .map(_ => Right(()))
      .recover { case ex: Exception =>
        logger.error("Error updating live activity mapping", ex)
        Left(new RepositoryException(ex, "Error updating live activity mapping"))
      }
  }

  override def updateMappingActiveChannel(id: String, isActive: Boolean): Future[Result[Unit]] = {
    updateMappingById(id, isChannelActive = Some(isActive))
  }

  override def updateMappingLiveEvent(id: String, isLive: Boolean): Future[Result[Unit]] = {
    updateMappingById(id, isEventLive = Some(isLive))
  }

  override def updateMappingLastEvent(id: String, lastEventId: String, lastEventUpdate: ZonedDateTime): Future[Result[Unit]] = {
    updateMappingById(id, lastEventId = Some(lastEventId), lastEventUpdate = Some(lastEventUpdate))
  }

  override def getMappingById(
      id: String
  ): Future[Result[LiveActivityMapping]] = {
    val getItemRequest = GetItemRequest.builder()
      .tableName(tableName)
      .key(Map(idField -> AttributeValue.fromS(id)).asJava)
      .consistentRead(true)
      .build()
    client
      .getItem(getItemRequest)
      .toScala
      .map { result =>
        if (result.hasItem()) {
          val parsed = fromAttributeMap[LiveActivityMapping](result.item().asScala.toMap)
          parsed match {
            case JsSuccess(mapping, _) => Right(mapping)
            case JsError(errors) =>
              logger.error(s"Error parsing live activity mapping: $errors")
              Left(new ChannelMappingDataException(id, "Unable to parse live activity mapping"))
          } 
        } else {
          Left(new ChannelMappingDataException(id, "Live Activity mapping not found"))
        }
      }
      .recover({ case ex: Exception =>
        logger.error("Error getting live activity mapping", ex)
        Left(new RepositoryException(ex, "Error getting live activity mapping"))
      })
  }

  override def deleteMappingById(
      id: String
  ): Future[Result[Unit]] = {
    val deleteItemRequest = DeleteItemRequest.builder()
      .tableName(tableName)
      .key(Map(idField -> AttributeValue.fromS(id)).asJava)
      .build()
    client
      .deleteItem(deleteItemRequest)
      .toScala
      .map(_ => Right(()))
      .recover({ case ex: Exception =>
        logger.error("Error deleting live activity mapping", ex)
        Left(new RepositoryException(ex, "Error deleting live activity mapping"))
      })
  }

}

package com.gu.liveactivities

import aws.AsyncDynamo
import aws.DynamoJsonConversions.{fromAttributeMap, toAttributeMap}
import cats.syntax.all._
import com.amazonaws.services.dynamodbv2.model._
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json._
import tracking.Repository.RepositoryResult
import tracking.RepositoryError

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._


// MODELS /////////////////////////////////////////////

sealed trait LiveActivityData
case class FootballLiveActivity(
                                 homeTeam: String,
                                 awayTeam: String,
                                 articleUrl: String
                               ) extends LiveActivityData

object FootballLiveActivity {
  implicit val format: OFormat[FootballLiveActivity] = Json.format[FootballLiveActivity]
}

object LiveActivityData {
  implicit val format: OFormat[LiveActivityData] = new OFormat[LiveActivityData] {
    def writes(data: LiveActivityData): JsObject = data match {
      case f: FootballLiveActivity =>
        FootballLiveActivity.format.writes(f) + ("type" -> JsString("football"))
    }
    def reads(json: JsValue): JsResult[LiveActivityData] =
      (json \ "type").validate[String].flatMap {
        case "football" => FootballLiveActivity.format.reads(json)
        case other      => JsError(s"Unknown LiveActivityData type: $other")
      }
  }
}

case class LiveActivityMapping(
                                liveActivityId: String,
                                channelId: String,
                                data: Option[LiveActivityData]
                              )
object LiveActivityMapping {
  implicit val format: OFormat[LiveActivityMapping] = Json.format[LiveActivityMapping]
}



// REPOSITORY /////////////////////////////////////////////

trait ChannelMappingsRepository {
  def saveMapping(mapping: LiveActivityMapping): Future[RepositoryResult[Unit]]
  def deleteMappingByActivityId(id: String): Future[RepositoryResult[Unit]]
  def getMappingByActivityId(
      id: String
  ): Future[RepositoryResult[LiveActivityMapping]]
}

class LiveActivityChannelRepository(client: AsyncDynamo, tableName: String)(
    implicit ec: ExecutionContext
) extends ChannelMappingsRepository {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)
  private val IdField = "liveActivityId"

  override def saveMapping(mapping: LiveActivityMapping): Future[RepositoryResult[Unit]] = {
    val putItemRequest = new PutItemRequest(tableName, toAttributeMap(mapping).asJava)
    client.putItem(putItemRequest) map { _ => Right(()) }
  }

  override def getMappingByActivityId(id: String): Future[RepositoryResult[LiveActivityMapping]] = {
    val getItemRequest = new GetItemRequest()
      .withTableName(tableName)
      .withKey(Map(IdField -> new AttributeValue().withS(id)).asJava)
      .withConsistentRead(true)

    client.get(getItemRequest) map { result =>
      for {
        item <- Either.fromOption(Option(result.getItem), RepositoryError("Live Activity not found"))
        parsed <- Either.fromOption(fromAttributeMap[LiveActivityMapping](item.asScala.toMap).asOpt, RepositoryError("Unable to parse live activity mapping"))
      } yield parsed
    }
  }

  override def deleteMappingByActivityId(id: String): Future[RepositoryResult[Unit]] = {
    val deleteItemRequest = new DeleteItemRequest()
      .withTableName(tableName)
      .withKey(Map(IdField -> new AttributeValue().withS(id)).asJava)
    client.deleteItem(deleteItemRequest) map { _ => Right(()) }
  }

}

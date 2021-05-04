package notification.services

import com.gu.{AppIdentity, AwsIdentity}
import play.api.{Configuration => PlayConfig}

class Configuration(conf: PlayConfig, identity: AppIdentity) {

  lazy val apiKeys: Set[String] = conf.get[Seq[String]]("notifications.api.secretKeys").toSet
  lazy val frontendNewsAlertEndpoint: String = conf.get[String]("notifications.frontendNewsAlert.endpoint")
  lazy val frontendNewsAlertApiKey: String = conf.get[String]("notifications.frontendNewsAlert.apiKey")
  lazy val dynamoReportsTableName: String = conf.get[String]("db.dynamo.reports.table-name")
  lazy val dynamoScheduleTableName: String = conf.get[String]("db.dynamo.schedule.table-name")
  lazy val newsstandRestrictedApiKeys: Set[String] = conf.get[Seq[String]]("notifications.api.newsstandRestrictedKeys").toSet

  lazy val mapiEndpointBase: String = conf.get[String]("mapi.base")

  lazy val fastlyApiEndpoint: String = conf.get[String]("notifications.fastly.apiUrl")
  lazy val fastlyKey: String = conf.get[String]("notifications.fastly.fastlyKey")
  lazy val fastlyService: String = conf.get[String]("notifications.fastly.fastlyService")

  lazy val newsstandShards: Int = conf.get[Int]("newsstand.shards")
  lazy val stage = identity match {
    case AwsIdentity(_, _, stage, _) => stage
    case _ => "DEV"
  }

}

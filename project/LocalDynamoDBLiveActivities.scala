import com.localytics.sbt.dynamodb.DynamoDBLocalKeys
import com.localytics.sbt.dynamodb.DynamoDBLocalKeys._
import sbt._

object LocalDynamoDBLiveActivities {

  val settings: Seq[Setting[_]] = DynamoDBLocalKeys.baseDynamoDBSettings ++ Seq(
    dynamoDBLocalDownloadDir := file("dynamodb-local-live-activities"),
    dynamoDBLocalInMemory := true,
    dynamoDBLocalVersion := "2018-04-11",
    dynamoDBLocalPort := 8002
  )
}

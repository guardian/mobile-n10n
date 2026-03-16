import com.localytics.sbt.dynamodb.DynamoDBLocalKeys
import com.localytics.sbt.dynamodb.DynamoDBLocalKeys._
import sbt._

object LocalDynamoDBLiveActivities {

  val settings: Seq[Setting[_]] = DynamoDBLocalKeys.baseDynamoDBSettings ++ Seq(
    dynamoDBLocalDownloadDir := file("dynamodb-local-live-activities"),
    dynamoDBLocalInMemory := true,
    dynamoDBLocalVersion := "latest",
//    dynamoDBLocalSharedDB := true, // Add this line
    dynamoDBLocalPort := 8002
  )
}

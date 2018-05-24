import sbt._
import com.localytics.sbt.dynamodb.DynamoDBLocalKeys
import com.localytics.sbt.dynamodb.DynamoDBLocalKeys._

object LocalDynamoDBCommon {

  val settings: Seq[Setting[_]] = DynamoDBLocalKeys.baseDynamoDBSettings ++ Seq(
    dynamoDBLocalDownloadDir := file("dynamodb-local-common"),
    dynamoDBLocalInMemory := true,
    dynamoDBLocalVersion := "2018-04-11",
    dynamoDBLocalPort := 8000
  )

}
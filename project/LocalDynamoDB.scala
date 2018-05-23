import sbt._
import com.localytics.sbt.dynamodb.DynamoDBLocalKeys
import com.localytics.sbt.dynamodb.DynamoDBLocalKeys._

object LocalDynamoDB {

  val settings: Seq[Setting[_]] = DynamoDBLocalKeys.baseDynamoDBSettings ++ Seq(
    dynamoDBLocalDownloadDir := file("dynamodb-local"),
    dynamoDBLocalInMemory := true,
    dynamoDBLocalVersion := "latest"
  )

}
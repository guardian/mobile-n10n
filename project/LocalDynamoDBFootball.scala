import com.localytics.sbt.dynamodb.DynamoDBLocalKeys
import com.localytics.sbt.dynamodb.DynamoDBLocalKeys._
import sbt._

object LocalDynamoDBFootball {

  val settings: Seq[Setting[_]] = DynamoDBLocalKeys.baseDynamoDBSettings ++ Seq(
    dynamoDBLocalDownloadDir := file("dynamodb-local-football"),
    dynamoDBLocalInMemory := true,
    dynamoDBLocalVersion := "latest",
    dynamoDBLocalPort := 8003,
  )
}

package aws
import org.joda.time.LocalDate
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest
import utils.MobileAwsCredentialsProvider

object KinesisTelemetry {
  val client: KinesisAsyncClient = KinesisAsyncClient.builder()
    .region(Region.EU_WEST_1)
    .credentialsProvider(MobileAwsCredentialsProvider.mobileAwsCredentialsProviderv2)
    .build()

  def putRecord(data: String): Unit = {
    val putRecordRequest = PutRecordRequest.builder()
      .streamName("telemetry-stream")
      .partitionKey(LocalDate.now().toString)
      .data(SdkBytes.fromByteArray(data.getBytes))
      .build()

    client.putRecord(putRecordRequest)
  }
}



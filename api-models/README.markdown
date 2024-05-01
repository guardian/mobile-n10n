# Mobile Notifications API Client
[![mobile-notifications-api-models Scala version support](https://index.scala-lang.org/guardian/mobile-n10n/mobile-notifications-api-models/latest-by-scala-version.svg?platform=jvm)](https://index.scala-lang.org/guardian/mobile-n10n/mobile-notifications-api-models)
Scala client for the Guardian Mobile Notifications API.

## Integrating with your application
Add to build.sbt: `libraryDependencies += "com.gu" %% "mobile-notifications-client" % "0.6.0"`
## Configure API Client
### Implement `HttpProvider` trait methods. 
This provider will be used by API Client to make an actual calls from your service. Example trait implementation:

```scala
import com.gu.mobile.notifications.client.HttpProvider

object NotificationHttpProvider extends HttpProvider {

  private def extract(response: Response): HttpResponse = {
    if (response.status >= 200 && response.status < 300)
      HttpOk(response.status, response.body)
    else
      HttpError(response.status, response.body)
  }
  
  override def post(url: String, contentType: ContentType, body: Array[Byte]): Future[HttpResponse] = {
    WS.url(url)
      .withHeaders("Content-Type" -> s"${contentType.mediaType}; charset=${contentType.charset}")
      .post(body)
      .map(extract)
  }

  override def get(url: String): Future[HttpResponse] = WS.url(url).get().map(extract)
}
```
Hint: *it is recommended to prefix name of this implementation with your application/service/artifact name to avoid name collisions*.
### Create configured Notifications API Client

```scala
import com.gu.mobile.notifications.client.ApiClient

val httpProvider = <your http provider instance>
val client = ApiClient(
  host = "http://notifications-host.com", 
  apiKey = "API-KEY", 
  httpProvider = httpProvider, 
  legacyHost = "http://old-notifications-host.com",
  legacyApiKey = "OLD-API-KEY"
)
```
## Using client
### Sending notification
You have to construct notification payload using one of the case classes from `com.gu.mobile.notifications.client.models.NotificationPayload` hierarchy
and send that object through the wire using `ApiClient.send` method. 
#### Example: sending Breaking News alert
```scala
val payload = BreakingNewsPayload(
  message = "195 countries and nearly 150 world leaders including Barack Obama and Xi Jinping meet in Paris for COP21 UN climate change conference",
  sender = "test sender",
  imageUrl = None,
  thumbnailUrl = None,
  link = ExternalLink("http://mylink"),
  importance = Importance.Major,
  topic = Set(BreakingNewsUk, "environment/climate-change"))
)

client.send(payload)
```
### Error handling
If you take a look at `ApiClient.send` method signature, you can spot its return type:

```scala
def send(notificationPayload: NotificationPayload): Future[Either[ApiClientError, Unit]]
```

If you're interesed in root error cause, you can pattern match on Either's `Left` side and errors from `com.gu.mobile.notifications.client.ApiClientError` hierarchy.
For instance:

```scala
client.send(...).map { case Left(e: CompositeApiError) => ... }
```
## Releasing the library
This repo uses [`gha-scala-library-release-workflow`](https://github.com/guardian/gha-scala-library-release-workflow)
to automate publishing releases (both full & preview releases) - see
[**Making a Release**](https://github.com/guardian/gha-scala-library-release-workflow/blob/main/docs/making-a-release.md).
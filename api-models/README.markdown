# Mobile Notifications API Client

[![Maven Central](https://img.shields.io/maven-central/v/com.gu/mobile-notifications-client_2.11.svg)](https://maven-badges.herokuapp.com/maven-central/com.gu/mobile-notifications-client_2.11)
[![Project status](https://img.shields.io/badge/status-active-brightgreen.svg)](#status)

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

Because of the incompatibility of the play-json module between version 2.3 and 2.4, a different branch has been created.
Each change that has been merged into ````master```` MUST be merged into the ````play-2.4```` branch

Once this is done, make sure to run ````sbt release```` on both branches

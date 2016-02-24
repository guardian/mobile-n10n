package notification.services.frontend

import java.net.URI
import javax.inject.Inject

import notification.services.Configuration
import play.api.libs.ws.WSClient

class FrontendAlertsSupport @Inject()(configuration: Configuration, wsClient: WSClient) {
  val frontendConfig = FrontendAlertsConfig(new URI(configuration.frontendNewsAlertEndpoint), configuration.frontendNewsAlertApiKey)
  val frontendAlerts = new FrontendAlerts(frontendConfig, wsClient)
}

case class FrontendAlertsConfig(endpoint: URI, apiKey: String)

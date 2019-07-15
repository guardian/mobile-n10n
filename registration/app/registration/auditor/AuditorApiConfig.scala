package registration.auditor

case class AuditorApiConfig(url: String, apiKey: String)

case class AuditorGroupConfig(contentApiConfig: AuditorApiConfig, paApiConfig: AuditorApiConfig)
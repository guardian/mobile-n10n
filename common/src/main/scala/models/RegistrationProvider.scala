package models

import play.api.libs.json._

trait RegistrationProvider {
  def value: String
}

object RegistrationProvider {

  implicit val providerJF: Format[RegistrationProvider] = new Format[RegistrationProvider] {
    override def reads(json: JsValue): JsResult[RegistrationProvider] = json match {
      case JsString("azure") => JsSuccess(Azure)
      case JsString("FCM") => JsSuccess(FCM)
      case JsString("AzureWithFirebase") => JsSuccess(AzureWithFirebase)
      case _ => JsSuccess(Unknown)
    }
    override def writes(o: RegistrationProvider): JsValue = JsString(o.value)
  }

  case object Azure extends RegistrationProvider {
    val value = "azure"
  }
  case object FCM extends RegistrationProvider {
    val value = "FCM"
  }
  case object AzureWithFirebase extends RegistrationProvider {
    val value = "AzureWithFirebase"
  }
  case object Unknown extends RegistrationProvider {
    val value = "unknown"
  }
}


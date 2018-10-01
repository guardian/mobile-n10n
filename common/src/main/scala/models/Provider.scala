package models

import ai.x.play.json.Jsonx
import play.api.libs.json._

sealed trait Provider {
  def value: String
}

object Provider {

  implicit val providerJF: Format[Provider] = new Format[Provider] {
    override def reads(json: JsValue): JsResult[Provider] = json match {
      case JsString("azure") => JsSuccess(Azure)
      case JsString("FCM") => JsSuccess(FCM)
      case _ => JsSuccess(Unknown)
    }
    override def writes(o: Provider): JsValue = JsString(o.value)
  }

  case object Azure extends Provider {
    val value = "azure"
  }
  case object FCM extends Provider {
    val value = "FCM"
  }
  case object Unknown extends Provider {
    val value = "unknown"
  }
}

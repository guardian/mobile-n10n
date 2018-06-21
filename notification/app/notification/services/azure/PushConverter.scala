package notification.services.azure

import azure.RawPush
import notification.models.Push


trait PushConverter {
  def toRawPush(push: Push): Option[RawPush]
}

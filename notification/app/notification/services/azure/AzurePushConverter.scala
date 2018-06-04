package notification.services.azure

import azure.RawPush
import notification.models.Push


trait AzurePushConverter {
  def toRawPush(push: Push): Option[RawPush]
}

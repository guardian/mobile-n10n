package fcmworker.models.payload

object Editions {

  sealed trait Edition

  case object UK extends Edition {
    override val toString = "uk"
  }

  case object US extends Edition {
    override val toString = "us"
  }

  case object AU extends Edition {
    override val toString = "au"
  }

  case object International extends Edition {
    override val toString = "international"
  }

  object Edition {
    val fromString: PartialFunction[String, Edition] = {
      case "uk" => UK
      case "us" => US
      case "au" => AU
      case "international" => International
    }
  }
}


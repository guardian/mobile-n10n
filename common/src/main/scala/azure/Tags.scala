package azure

import models.{UserId, Topic}

case class Tag(encodedTag: String)

object Tag {
  import Tags._

  def fromTopic(t: Topic): Tag = {
    Tag(s"${TopicTagPrefix}Base16:${Base16.encode(t.toString)}")
  }

  def fromUserId(u: UserId): Tag = {
    Tag(s"$UserTagPrefix${u.id}")
  }

}

case class Tags(tags: Set[Tag] = Set.empty) {
  import Tags._

  def asSet: Set[String] = tags.map(_.encodedTag)

  def findUserId: Option[UserId] = encodedTags
    .find(_.matches(UserTagRegex.regex))
    .map { case UserTagRegex(UserId(uuid)) => UserId(uuid) }

  def decodedTopics: Set[Topic] = {
    val topics = encodedTags.collect{ case TopicTagRegex(encodedTopic) => Base16.decode(encodedTopic) }
    topics.map(Topic.fromString).flatMap(_.toOption)
  }

  def withUserId(userId: UserId): Tags = copy(tags + Tag.fromUserId(userId))

  def withTopics(topics: Set[Topic]): Tags = copy(tags ++ topics.map(Tag.fromTopic))

  private[this] def encodedTags = tags.map(_.encodedTag)
}

object Tags {
  val UserTagPrefix = "user:"
  val TopicTagPrefix = "topic:"
  val UserTagRegex = """user:(.*)""".r
  val TopicTagRegex = """topic:Base16:(.*)""".r

  def fromStrings(tags: Set[String]): Tags = Tags(tags.map(Tag(_)))
}

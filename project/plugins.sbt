libraryDependencies ++= Seq(
  "com.github.docker-java" % "docker-java-core" % "3.2.12",
  "com.github.docker-java" % "docker-java-transport-httpclient5" % "3.2.12"
)

resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.0")

addSbtPlugin("com.gu" % "sbt-riffraff-artifact" % "1.1.8")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.10")

addSbtPlugin("com.localytics" % "sbt-dynamodb" % "2.0.3")

libraryDependencies += "org.vafer" % "jdeb" % "1.8" artifacts (Artifact("jdeb", "jar", "jar"))

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.13")

addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.6")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.0")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.4")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")

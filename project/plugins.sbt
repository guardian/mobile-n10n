resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.11")

addSbtPlugin("com.gu" % "sbt-riffraff-artifact" % "0.9.9")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.6")

addSbtPlugin("com.localytics" % "sbt-dynamodb" % "1.5.5")

libraryDependencies += "org.scalariform" %% "scalariform" % "0.1.7"

libraryDependencies += "org.vafer" % "jdeb" % "1.6" artifacts (Artifact("jdeb", "jar", "jar"))

resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.3")

addSbtPlugin("com.gu" % "sbt-riffraff-artifact" % "0.8.4")

addSbtPlugin("com.localytics" % "sbt-dynamodb" % "1.5.5")

libraryDependencies += "org.scalariform" %% "scalariform" % "0.1.7"
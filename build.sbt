name := """somnus"""
organization := "ch.chronobiology"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.3"

resolvers += Resolver.jcenterRepo

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test
javaOptions in Test += "-Dconfig.file=conf/application.test.conf"

libraryDependencies += "com.h2database" % "h2" % "1.4.192"
libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-slick" % "3.0.3",
  "com.typesafe.play" %% "play-slick-evolutions" % "3.0.3"
)

libraryDependencies ++= Seq(
  "com.mohiva" %% "play-silhouette" % "5.0.0",
  "com.mohiva" %% "play-silhouette-password-bcrypt" % "5.0.0",
  "com.mohiva" %% "play-silhouette-crypto-jca" % "5.0.0",
  "com.mohiva" %% "play-silhouette-persistence" % "5.0.0",
  "com.mohiva" %% "play-silhouette-testkit" % "5.0.0" % "test"
)

libraryDependencies += "net.codingwell" %% "scala-guice" % "4.1.1"

libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.2.18"

wartremoverErrors in(Compile, compile) ++= Warts.unsafe diff List(
  Wart.NonUnitStatements,
  Wart.DefaultArguments,
  Wart.Throw
)
wartremoverErrors in Test ++= Warts.unsafe diff List(
  Wart.NonUnitStatements,
  Wart.DefaultArguments,
  Wart.OptionPartial,
  Wart.Null,
  Wart.Throw,
  Wart.Any,
  Wart.TraversableOps
)
wartremoverExcluded ++= routes.in(Compile).value

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "ch.chronobiology.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "ch.chronobiology.binders._"

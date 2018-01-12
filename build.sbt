name := """somnus"""
organization := "ch.chronobiology"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.3"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test
javaOptions in Test += "-Dconfig.file=conf/application.test.conf"

libraryDependencies += "com.h2database" % "h2" % "1.4.192"
libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-slick" % "3.0.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "3.0.0"
)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "ch.chronobiology.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "ch.chronobiology.binders._"

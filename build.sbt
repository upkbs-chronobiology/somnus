name := """somnus"""
organization := "ch.chronobiology"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.3"

scalacOptions ++= Seq("-Ywarn-unused")

resolvers += Resolver.jcenterRepo

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test
javaOptions in Test += "-Dconfig.file=conf/application.test.conf"

libraryDependencies += "com.h2database" % "h2" % "1.4.196"
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

libraryDependencies += "com.opencsv" % "opencsv" % "4.1"

wartremoverErrors in(Compile, compile) ++= Warts.unsafe diff List(
  Wart.NonUnitStatements,
  Wart.DefaultArguments,
  Wart.Throw,
  Wart.Null
)
wartremoverErrors in Test ++= Warts.unsafe diff List(
  Wart.NonUnitStatements,
  Wart.DefaultArguments,
  Wart.OptionPartial,
  Wart.Null,
  Wart.Throw,
  Wart.Any,
  Wart.TraversableOps,
  Wart.Var
)
wartremoverExcluded ++= routes.in(Compile).value

// add scalastyle to compile task
lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")
compileScalastyle := scalastyle.in(Compile).toTask("").value
(compile in Compile) := ((compile in Compile) dependsOn compileScalastyle).value

// add scalastyle to test task
lazy val testScalastyle = taskKey[Unit]("testScalastyle")
testScalastyle := scalastyle.in(Test).toTask("").value
(test in Test) := ((test in Test) dependsOn testScalastyle).value

(scalastyleConfig in Test) := baseDirectory.value / "scalastyle-test-config.xml"

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "ch.chronobiology.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "ch.chronobiology.binders._"

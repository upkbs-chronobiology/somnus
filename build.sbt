import java.time.Instant
import java.time.temporal.ChronoUnit

import scala.io.Source
import scala.tools.nsc.io.File

import com.typesafe.sbt.packager.docker.DockerVersion

name := """somnus"""
organization := "ch.chronobiology"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.10"

scalacOptions ++= Seq("-Ywarn-unused")

resolvers += Resolver.jcenterRepo

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test
javaOptions in Test += "-Dconfig.file=conf/application.test.conf"

libraryDependencies += "com.h2database" % "h2" % "1.4.197"
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

libraryDependencies += "javax.xml.bind" % "jaxb-api" % "2.3.0"

libraryDependencies += "org.apache.commons" % "commons-collections4" % "4.4"

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


// ~ Integration testing ~

addCommandAlias("testServe", "testProd -Dplay.http.secret.key='dummy-secret' " +
  "-Dconfig.file=conf/application.test.conf -DtestServe=true")


// ~ Docker publication ~

dockerVersion := Some(DockerVersion(19, 3, 11, Some("ce")))
dockerEntrypoint := Seq("bin/somnus", "-Dconfig.file=conf/application.dist.conf")
dockerExposedPorts := Seq(9000)

dockerRepository := Some("docker.pkg.github.com/upkbs-chronobiology")
dockerUsername := Some("somnus")

val prepConfigForDocker = taskKey[Unit]("Append db password to target dist config file")
prepConfigForDocker := {
  val stageLocation = (Docker / stage).value

  val configFile = File(s"$stageLocation/opt/docker/conf/application.dist.conf")

  val dbPseudoDomain = "somnus-db"
  val mappedLines = Source.fromFile(configFile.path).getLines()
    .map(_.replaceAll("tcp://localhost", s"tcp://$dbPseudoDomain")).toSeq

  // Database password should be provided through a command line flag (-Dslick.dbs.default.db.password) or environment
  // variable (slick.dbs.default.db.password).

  // Application secret should be provided through a command line flag (-Dplay.http.secret.key) or environment variable
  // (play.http.secret.key).

  configFile.writeAll(mappedLines.mkString("\n"))
}

val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
val timestamp = now.toString.replace(":", "-")
(version in Docker) := (version in Docker).value + "_" + timestamp
(Docker / publishLocal) := ((Docker / publishLocal) dependsOn prepConfigForDocker).value


// ~ Releasing ~

import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepTask(publishLocal in Docker),
  setNextVersion,
  commitNextVersion,
  pushChanges
)

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.15"

lazy val root = (project in file("."))
  .settings(
    name := "example-http4s-tapir"
  )

lazy val http4sVersion = "0.23.28"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "io.circe" %% "circe-generic" % "0.14.9",
  "com.softwaremill.sttp.tapir" %% "tapir-core" % "1.11.7",
  "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % "1.11.7",
  "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % "1.11.7",
  "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.11.7"
)

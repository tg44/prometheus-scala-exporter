name := "prometheus-scala-exporter"

version := "0.2.3"

scalaVersion := "2.13.1"

crossScalaVersions := Seq("2.12.7", "2.13.1")

libraryDependencies ++= {
  val akkaV     = "2.6.4"
  Seq(
    "com.typesafe.akka"  %% "akka-actor-typed"     % akkaV,
    "com.typesafe.akka"  %% "akka-stream"          % akkaV,
    "com.typesafe.akka"  %% "akka-stream-typed"    % akkaV,
    "org.typelevel"      %% "cats-effect"          % "2.2.0",
    "org.scalatest"      %% "scalatest"            % "3.0.8" % Test,
  )
}

organization := "xyz.tg44"
githubOwner := "tg44"
githubRepository := "prometheus-scala-exporter"
githubTokenSource := TokenSource.Environment("GITHUB_TOKEN")
githubActor := sys.env.getOrElse("GITHUB_USER", "REPLACE_ME")

name := "engager"
version := "0.1.0"
scalaVersion := "2.13.12"

val AkkaVersion     = "2.8.5"
val AkkaHttpVersion = "10.5.3"
val CirceVersion    = "0.14.6"
val SlickVersion    = "3.5.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream"      % AkkaVersion,
  "com.typesafe.akka" %% "akka-http"        % AkkaHttpVersion,

  "io.circe" %% "circe-core"    % CirceVersion,
  "io.circe" %% "circe-generic" % CirceVersion,
  "io.circe" %% "circe-parser"  % CirceVersion,

  "de.heikoseeberger" %% "akka-http-circe" % "1.39.2",

  "com.typesafe.akka" %% "akka-stream-kafka" % "4.0.2",

  "co.elastic.clients"         % "elasticsearch-java" % "8.11.4",
  "com.fasterxml.jackson.core" % "jackson-databind"   % "2.15.3",

  "com.typesafe.slick" %% "slick"          % SlickVersion,
  "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
  "org.postgresql"      % "postgresql"     % "42.7.1",

  "com.typesafe" % "config" % "1.4.3",

  "ch.qos.logback"    % "logback-classic" % "1.4.14",
  "com.typesafe.akka" %% "akka-slf4j"     % AkkaVersion,

  "org.scalatest"     %% "scalatest"                             % "3.2.17"    % Test,
  "com.typesafe.akka" %% "akka-http-testkit"                    % AkkaHttpVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit"                  % AkkaVersion     % Test,
  "com.dimafeng"      %% "testcontainers-scala-scalatest"        % "0.41.0"    % Test,
  "com.dimafeng"      %% "testcontainers-scala-kafka"            % "0.41.0"    % Test,
  "com.dimafeng"      %% "testcontainers-scala-elasticsearch"    % "0.41.0"    % Test,
  "com.dimafeng"      %% "testcontainers-scala-postgresql"       % "0.41.0"    % Test
)

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked"
)

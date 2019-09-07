name := "ScalaBank"

organization := "pl.ark.chr.scalabank"

version := "0.1"

scalaVersion := "2.13.0"

lazy val akkaVersion = "2.5.25"
lazy val scalaTestVersion = "3.0.8"
lazy val akkaHttpVersion = "10.1.9"
lazy val cassandraVersion = "0.99"
lazy val logbackVersion = "1.2.3"
lazy val avro4sVersion = "3.0.0"
lazy val akkaPersistenceInMemoryVersion = "2.5.15.2"

assemblyMergeStrategy in assembly := {
  case "META-INF/io.netty.versions.properties" => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

resolvers += Resolver.bintrayRepo("dnvriend", "maven")

libraryDependencies ++= Seq(
  // AKKA
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,

  // SERIALIZATION
  "com.sksamuel.avro4s" %% "avro4s-core" % avro4sVersion,

  // LOGGING
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion,

  // Cassandra
  "com.typesafe.akka" %% "akka-persistence-cassandra" % cassandraVersion,
//  "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % cassandraVersion % Test,

  // TESTING
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
  "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
  "com.github.dnvriend" %% "akka-persistence-inmemory" % akkaPersistenceInMemoryVersion % Test
)
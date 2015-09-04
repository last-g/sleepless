name := "sleepless"

version := "1.0"

scalaVersion := "2.11.7"

resolvers += "hseeberger at bintray" at "http://dl.bintray.com/hseeberger/maven"

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.3.13",
    "com.typesafe.akka" % "akka-stream-experimental_2.11" % "1.0",
    "com.typesafe.akka" % "akka-http-core-experimental_2.11" % "1.0",
    "com.typesafe.akka" % "akka-http-experimental_2.11" % "1.0",
    "com.typesafe.akka" % "akka-http-spray-json-experimental_2.11" % "1.0",
    "io.spray" %% "spray-client" % "1.3.+",
    "net.ruippeixotog" %% "scala-scraper" % "0.1.+",
    "com.github.tototoshi" %% "scala-csv" % "1.2.2"
)

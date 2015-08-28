name := "sleepless"

version := "1.0"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.+",
  "io.spray" %% "spray-client" % "1.3.+",
  "net.ruippeixotog" %% "scala-scraper" % "0.1.+",
  "com.github.tototoshi" %% "scala-csv" % "1.2.2"
)

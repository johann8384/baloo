name := "baloo"

version := "0.1"

scalaVersion := "2.9.2"

resolvers += "Twitter Maven Repo" at "http://maven.twttr.com/"

resolvers += "Sonatype Maven Repo" at "http://oss.sonatype.org/content/repositories/releases"

resolvers += "Mozilla Metrics Snapshots Repo" at "http://mozilla-metrics.github.com/maven2/snapshots"

libraryDependencies += "com.twitter" % "finagle-core" % "5.1.0"

libraryDependencies += "com.twitter" % "finagle-http" % "5.1.0"

libraryDependencies += "log4j" % "log4j" % "1.2.16"

libraryDependencies += "org.codehaus.jackson" % "jackson-core-asl" % "1.9.7"

libraryDependencies += "org.codehaus.jackson" % "jackson-mapper-asl" % "1.9.7"

libraryDependencies += "org.apache.kafka" % "kafka-core" % "0.7.0" changing()


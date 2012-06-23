# Baloo #

Version: 0.1  

#### REST service for Mozilla Metrics. This is an intended as a Scala based replacement for Bagheera using Kafka as the data backend and using Finagle for the service framework rather than using Netty directly. Check the links below and read more about these projects before proceeding. ####

### Version Compatability ###
This code is built with the following assumptions.  You may get mixed results if you deviate from these versions.

* [Finagle](http://twitter.github.com/finagle) 5.1.0
* [Kafka](http://incubator.apache.org/kafka) 0.7.0
* [Simple Build Tool](https://github.com/harrah/xsbt) 0.11.2

### Building ###
To compile the codebase you need Simple Build Tool (sbt). If you're using Mac OS X and have Homebrew you can install it like so:

`brew install sbt`

Then you should be able to compile the codebase (you don't need to call update everytime)  

`sbt update`
`sbt compile`

### Running an instance ###
To run a developer instance just use:

`sbt run`

### REST Request Format ###

Baloo takes POST data on _/namespace/id_. The service sends every entry for a particular _namespace_ to a Kafka topic. There it waits to be picked up by a consumer for further processing and/or more permanent persistence.

Here's a quick rundown of HTTP return codes that Baloo could send back (this isn't comprehensive but rather the most common ones):

* 201 Created - returned if everything was submitted successfully (default)
* 406 Not Acceptable - returned if the POST failed validation in some manner
* 500 Server Error - something went horribly wrong and you should check the logs

### License ###
All aspects of this software are distributed under Apache Software License 2.0. See LICENSE file for full license text.

### Contributors ###

* Xavier Stevens ([@xstevens](http://twitter.com/xstevens))

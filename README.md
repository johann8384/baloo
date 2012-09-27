# Baloo #

Version: 0.1  

#### REST service for Mozilla Metrics. This is an experiment of what a Scala based replacement for Bagheera might look like; using Finagle for the service framework rather than using Netty directly. Check the links below and read more about these projects before proceeding. ####

### Version Compatability ###
This code is built with the following assumptions.  You may get mixed results if you deviate from these versions.

* [Finagle](http://twitter.github.com/finagle) 5.1.0
* [Kafka](http://incubator.apache.org/kafka) 0.7.1
* [Protocol Buffers](https://developers.google.com/protocol-buffers) 2.3.0+

### Building ###
To make a jar you can do:

`mvn package`

The jar file is then located under target.

### Running an instance ###
Make sure your Kafka and Zookeeper servers are running first (see Kafka documentation)

In order to run baloo on another machine you will probably want to use the dist assembly like so:

mvn assembly:assembly

The zip file now under the target directory should be deployed to BALOO_HOME on the remote server.

To run Baloo you can use bin/baloo or copy the init.d script by the same name from bin/init.d to /etc/init.d. The init script assumes an installation of baloo at /usr/lib/baloo, but this can be modified by changing the BALOO_HOME variable near the top of that script. Here is an example of using the regular baloo script:

bin/baloo 8080

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

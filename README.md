# The Paremus Remote Services Repository

This repository contains components implementing the OSGi Remote Services and Remote Service Admin specifications.

The implementations use Netty as a communications framework, and make use of Paremus Core components for security. There is also an RSA discovery provider based on top of the Paremus Clustering API. 

The components here provide a highly scalable, fast, low-latency remoting implementation capable of synchronous and asynchronous behaviours. This includes the serialization of Java Futures, OSGi Promises and OSGi PushStreams.

Furthermore Paremus RSA supports the concept of `scoping` for Remote Services, namely that Remote Services can be made visible to a subset of the frameworks connected by a discovery provider. This means that remote service visibility can be restricted to only frameworks where the service is relevant.  

## Repository Contents

This repository contains:

### com.paremus.dosgi.api

This project provides API for Paremus Remote Services, including constants and interfaces for scoping and multi-framework aware Remote Service Admin.

### com.paremus.dosgi.net, com.paremus.dosgi.net.promise.v1 & com.paremus.dosgi.net.test

The `com.paremus.dosgi.net` projects combine to provide an implementation of OSGi Remote Service Admin using Netty as a communications layer. The implementation supports the following advanced features

 * Support for OSGi Promises and Java Futures as defined by the `osgi.async` intent.
 * Enhanced support for OSGi Promises as arguments of remote calls
 * Support for OSGi PushStreams and PushEventSources as return values from remote calls
     * These return values fully support the laziness and back pressure associated with Push Streams
 * Support for security using TLS and client authentication
 * Support for multiple serialization mechanisms
     * A fast custom serialization provider derived from essence-rmi
     * Google Protocol Buffers
     * Java Serialization
 * Support for framework isolation, where multiple OSGi framework instances coexist in the same JVM, and Remote Services must be consumed from or published into a different OSGi framework from the one containing the Remote Services implementation

The integration tests for the dosgi net component demonstrate the use of the service, and validate the advanced features of the implementation.

Note that this implementation makes use of functions from the [Netty](https://netty.io) and [essence-rmi](https://github.com/davemssavage/essence-rmi) projects

### com.paremus.dosgi.discovery.cluster & com.paremus.dosgi.discovery.cluster.test

The `com.paremus.dosgi.discovery.cluster` project provides a discovery provider based on top of the Paremus Cluster API (typically gossip-based clustering). This discovery provider offers support for scoping, with local scopes able to be added and removed (controlling the scopes that the current node is interested in) and efficient delivery of service endpoint information only to nodes participating in suitable scopes.


### com.paremus.dosgi.topology.common & com.paremus.dosgi.topology.simple

The Simple Paremus Topology Manager implements a modified version of the `promiscuous` policy. It is scope aware and attempts to export services at "global" scope (i.e. visible to all) unless otherwise instructed by the service properties.

# How to build this repository

This repository can be built using Maven 3.5.4 and Java 9. The output bundles will work with Java 8, however DTLS 1.2 support is only available within the JDK since Java 9. On Java 8 the bouncy castle DTLS provider must be used instead.  

## Build profiles

By default the build will run with all tests, and lenient checks on copyright headers. To enable strict copyright checking (required for deployment) then the `strict-license-check` profile should be used, for example

    mvn -P strict-license-check clean install

If you make changes and do encounter licensing errors then the license headers can be regenerated using the `generate-licenses` profile

    mvn -P generate-licenses process-sources

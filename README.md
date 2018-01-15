# somnus

[![Build Status](https://travis-ci.org/upkbs-chronobiology/somnus.svg?branch=master)](https://travis-ci.org/upkbs-chronobiology/somnus)

Somnus is the code name for an electronic sleep log.
This is the main application managing sleep log data.

## Tech stack

This project is based on the Play framework with Scala as programming language.

## Requirements

You need to have **[Java 8+ (JDK)](http://www.oracle.com/technetwork/java/javase/downloads/index.html)** and **[sbt](https://www.scala-sbt.org/)** installed.

## Build & run

In order to build and run the application, execute `sbt run` from this application root directory.
This will run the server in dev mode on [localhost:9000](http://localhost:9000/).

### Running tests

All tests are executed with sbt's `test` goal, e.g. `sbt test` from the command line.

During tests, a different config file (`application.test.conf`) is used, and sbt is configured to automatically pick it up.
If you want to run tests from an IDE, you might need to set the following VM parameter manually: `-Dconfig.file=conf/application.test.conf`

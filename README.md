# somnus

[![Build Status](https://travis-ci.org/upkbs-chronobiology/somnus.svg?branch=master)](https://travis-ci.org/upkbs-chronobiology/somnus)

Somnus is the code name for a digital sleep log.
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

### Integration testing

For integration tests running against this back end, it is best to start it with the `testServe` command (`sbt testServe`).
This uses test configuration (in particular a volatile, in-memory db) and auto-creates a test user.

## Release

*We're now building docker images regularly from master, so this mechanism is now only relevant for version bumping.*

A new docker prod release can be done by running `sbt release`.
This should be the standard procedure for releasing, so we always have reliable versioning.
This does most of what's necessary for a release, including version bumping, tagging, and locally publishing docker images.
However, it does not deploy anything to an actual production server.

### Application docker image

*Manually creating an image is only relevant if not done through `sbt release` as described above.*

In order to build a distribution docker image, you can use `sbt docker:publishLocal`, or `sbt docker:publish`, respectively.
It will prompt you for the prod database password and automatically insert it into config.
The resulting image's name should be printed to the console; it has the form `somnus:<version>`.

In order to run it, you'll have to make sure the database container  is reachable under `somnus-db`,
e.g. by running starting the app container with `docker run -d --link <db-container-name>:somnus-db --name SomnusBE somnus:<version>`.

### Database docker image (legacy)

*This is not the preferred production setup anymore, but has been left here for reference.*

The database runs in a separate container than the main application in production.
As main application updates usually shouldn't affect the db image (short of schema changes), hence building that image is rarely necessary.
Schema updates should either be executed on a copy of the production db, then replace it, or they should be tested locally on a backup before prod execution.

However, to build a complete fresh H2 docker image, do the following:

- Pull and run the [`oscarfonts/h2` image](https://hub.docker.com/r/oscarfonts/h2/), e.g. `docker run -d -p 1521:1521 -p 81:81 --name=SomnusH2 oscarfonts/h2`.
- Locally run the application with the dist config file (`sbt "run -Dconfig.file=conf/application.dist.conf"` - quotes are crucial) and apply evolutions.
*Also, make sure to write down the generated application admin user's ("somnus") password, as printed to the console!*
*NB: After successful db upgrade, you should see a "Bad Request" error - this is expected, as localhost is not an allowed host in prod.*
- Using an H2 browser (the one of the container is likely at [`172.18.0.2:81`](http://172.18.0.2:81/)), connect to the db `jdbc:h2:tcp://localhost:1521/default` and verify tables have been generated.
Now, set a password for the "SA" user: `alter user SA set password '<new-password>'` (again, make sure to write it down).
- Stop the container, create an image out of it (`docker commit`) and put it into production, either through an online repository or by using `docker save`.

## Production setup (legacy)

*This is not the preferred production setup anymore, but has been left here for reference.*

When running the two containers (db and application) in production, either link them as described above, or properly set up networking:

- Make sure the two containers are part of the same network.
- On the application container, set a hosts file entry mapping `somnus-db` to the db container's IP.
-- In Portainer, this can be configured under "Advanced container settings" > "Network".
-- For local testing, the backend app container can be started with the argument `--add-host somnus-db:0.0.0.0`.

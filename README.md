# Excerpt-worker

This service generates PDF excerpt files based on input from Kafka topic.

### Related components:
* `excerpt-service-api` - service, which provides web api for excerpt generation functionality
* Kafka for retrieving messages from `excerpt-service-api`
* Ceph storage for result files persistence
* PostgreSQL database for excerpt info persistence (processing statuses etc.)

### Local development:
###### Prerequisites:
* Kafka is configured and running
* Ceph/S3-like storage is configured and running
* Database `excerpt` is configured and running

###### Database setup:
1. Create database `excerpt`
1. Run `/platform-db/changesets/excerpt/` script(s) from the `citus` repository

###### Configuration:
1. Check `src/main/resources/application-local.yaml` and replace if needed:
   * data-platform.datasource... properties with actual values from local db
   * data-platform.kafka.boostrap with url of local kafka
   * *-ceph properties with your ceph storage values
   * dso.url if current url is unavailable

###### Steps:
1. (Optional) Package application into jar file with `mvn clean package`
2. Add `--spring.profiles.active=local` to application run arguments
3. Run application with your favourite IDE or via `java -jar ...` with jar file, created above

### License
excerpt-worker is Open Source software released under the Apache 2.0 license.
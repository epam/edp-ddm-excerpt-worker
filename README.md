# Excerpt-worker

This service generates pdf excerpt files based on input from kafka topic
### Related components:
* `excerpt-service-api` - service, which provides web api for excerpt generation functionality
* Kafka for retrieving messages from excerpt-service-api
* Ceph storage for result files persistence
* PostgreSQL database for excerpt info persistence (processing statuses etc.)

### Local deployment:
###### Prerequisites:

* Kafka is configured and running
* Ceph storage is configured and running (either local or remote)
* Database `excerpt` is configured and running(as described in excerpt-service-api readme)

###### Steps:
1. Check `src/main/resources/application-local.yaml` and replace if needed:
    * data-platform.datasource... properties with actual values from local db
    * data-platform.kafka.boostrap with url of local kafka
    * *-ceph properties with your ceph storage values
    * dso.url if current url is unavailable
2. (Optional) Package application into jar file with `mvn clean package`
3. Add `--spring.profiles.active=local` to application run arguments
4. Run application with your favourite IDE or via `java -jar ...` with jar file, created on step 2

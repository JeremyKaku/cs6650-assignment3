# consumer-v3

This folder contains the Spring Boot consumer microservice for the assignment. The README below is based on the project's sources (pom.xml, application.properties, controllers, and services) and reflects the actual defaults used by the code.

Contents
- `src/main/java/com/chatflow/consumerv3/` - Java source code (controllers, models, services).
- `src/main/resources/application.properties` - Application configuration (defaults listed below).
- `src/main/setup-dynamodb-tables.sh` - Script to create required DynamoDB tables (same as `database/setup-dynamodb-tables.sh`).
- `pom.xml`, `mvnw`, `mvnw.cmd` - Maven wrapper and build config. The project is configured to use Java 17 (see `pom.xml`).
- `target/` - Build output (generated after running the build).

Important facts discovered in the code (authoritative)
- Java version required: 17 (property `java.version` in `pom.xml`).
- Server port (default): `8081` (set in `application.properties`).
- Exposed HTTP endpoints implemented in code:
  - `GET /health` — health check (see `HealthController`).
  - `GET /stats` — returns DynamoDB and consumer stats (see `StatsController`).
- DynamoDB table names (used by `DynamoDBService` and created by the setup script):
  - `chatflow-messages` (primary key: roomId [HASH], timestamp [RANGE]; GSIs: `UserMessagesIndex`, `MessageIdIndex`).
  - `chatflow-room-participation` (primary key: userId [HASH], roomId [RANGE]).
  - `chatflow-analytics` (primary key: metricType [HASH], entityId [RANGE]).
- SQS configuration (from `application.properties`):
  - `sqs.queue.url.prefix` = `https://sqs.us-west-2.amazonaws.com/590183786772/chatflow-`
  - `sqs.rooms.start` = `1`
  - `sqs.rooms.end` = `20`
  - The code generates queue URLs by appending `room-{n}.fifo` for `n` in the above range (see `MessageConsumer.generateQueueUrls`). Example queue URL produced by the code: `https://sqs.us-west-2.amazonaws.com/590183786772/chatflow-room-1.fifo`.
- `MessageConsumer` configuration defaults are controlled by `application.properties` and include:
  - `consumer.thread.count` = `80` (configured in `application.properties`).
  - `aws.region` = `us-west-2`.
  - `server.broadcast.url` = `http://chatflow-alb-1154142688.us-west-2.elb.amazonaws.com/api/broadcast` (this is a default ALB DNS value present in the repository — update this to your broadcast endpoint before running in your environment).
- `DynamoDBService` properties (from `application.properties`):
  - `dynamodb.batch.size` = `5000`.
  - `dynamodb.flush.interval.ms` = `1000`.
  - `dynamodb.writer.threads` = `8`.

Quick start (accurate)
1. Ensure Java 17 JDK is installed and available on PATH.
2. Build the service (from repo root):

```bash
# Uses the Maven wrapper in the repository
./mvnw -f consumer-v3/ clean package
```

This produces the runnable artifact: `consumer-v3/target/consumer-v3-0.0.1-SNAPSHOT.jar`.

3. Run the service:

```bash
java -jar consumer-v3/target/consumer-v3-0.0.1-SNAPSHOT.jar
```

4. Verify endpoints (defaults):

```bash
# Health
curl http://localhost:8081/health

# Stats
curl http://localhost:8081/stats
```

Configuration notes (exact)
- Edit `consumer-v3/src/main/resources/application.properties` to change any defaults (server port, AWS region, SQS prefix, DynamoDB table names, batch sizing, etc.). The application reads these properties via `@Value` annotations in the code.
- The `server.broadcast.url` property points to an ALB hostname in the repo. The consumer will call this URL synchronously for each message; if it is not reachable the consumer will mark the broadcast as failed and SQS will retry the message per its visibility timeout.

DynamoDB setup
- The repository includes `consumer-v3/src/main/setup-dynamodb-tables.sh` and `database/setup-dynamodb-tables.sh`. Both scripts create the three DynamoDB tables listed above. The scripts default to region `us-west-2` and an account ID `590183786772` (this account ID is used only in the SQS URL prefix in `application.properties`; the scripts do not require the account ID to create tables but the scripts print it as informational text).

Run the table creation script (requires AWS CLI and correct credentials):

```bash
bash consumer-v3/src/main/setup-dynamodb-tables.sh
# or from the database folder
bash database/setup-dynamodb-tables.sh
```

Load testing
- The repository `load-tests/endurance_test.sh` runs a Java client jar `target/client-part2-1.0-SNAPSHOT-jar-with-dependencies.jar` against a WebSocket URL `ws://chatflow-alb-1154142688.us-west-2.elb.amazonaws.com`.
- This consumer repository does not include that client project; the script is an example harness. To run it successfully you must build the client jar referenced or replace the command with your own load-test client.

Testing
- Run unit tests with:

```bash
./mvnw -f consumer-v3/ test
```

Logging & Metrics
- Logging levels and console pattern are configured in `application.properties`.
- The app exposes Spring actuator endpoints configured in `application.properties`: `health`, `metrics`, `info`, `dynamodb` (the latter is included in `management.endpoints.web.exposure.include`).

Warnings & safety notes (precise)
- The default `server.broadcast.url` in `application.properties` is a placeholder ALB DNS included in the repository and may not be reachable from your environment; update it before running production workloads.
- The SQS queue URL prefix and account number are set in `application.properties`; ensure these match your AWS account/queues or change them to a local SQS endpoint (LocalStack) for testing.
- The DynamoDB batch write implementation uses 25-item chunking (DynamoDB limit) and a retry loop with up to 3 retries; inspect `DynamoDBService.batchWriteWithRetry` if you need different retry behavior.

If you want, I can:
- Extract the exact property values into a small table in this README (already listed above), or
- Add a `consumer-v3/ENVIRONMENT.md` with step-by-step environment setup (local DynamoDB, LocalStack, or AWS credentials guidance).

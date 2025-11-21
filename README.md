# cs6650-assignment3

This repository contains code, scripts, and supporting assets for Assignment 3 of CS6650.
It includes a Spring Boot consumer service, database setup scripts, load-testing helpers, and monitoring assets.

This top-level README gives a quick orientation and links to focused READMEs in each folder.

Summary
- Project: Consumer microservice and supporting tools for load testing and database setup.
- Languages/Tools: Java (Spring Boot), Maven, Bash scripts, AWS (DynamoDB, optional RDS).

Repository layout
- `consumer-v3/` - Spring Boot consumer service (Java/Maven). See `consumer-v3/README.md` for details.
- `database/` - Scripts to create/setup database resources (DynamoDB tables). See `database/README.md`.
- `load-tests/` - Load and endurance test scripts (example harness). See `load-tests/README.md`.
- `monitoring/` - Monitoring assets (CloudWatch dashboard JSON). See `monitoring/README.md`.

Quick start (minimal)
1. Ensure Java 17 is installed and available on PATH (this project uses `java.version=17` in `consumer-v3/pom.xml`).
2. Build the service:

```bash
# from the repo root
./mvnw -f consumer-v3/ clean package
```

3. Run the service jar:

```bash
java -jar consumer-v3/target/consumer-v3-0.0.1-SNAPSHOT.jar
```

4. Run the example load test (note: the script references a client jar that is not present in this repo — see `load-tests/README.md`):

```bash
bash load-tests/endurance_test.sh
```

Prerequisites
- Java 17 (JDK).
- Bash/zsh shell for scripts (macOS/Linux).
- (Optional) AWS CLI configured with credentials and a default region if you plan to create AWS resources.

Recommended workflow
- Read `consumer-v3/README.md` for development and configuration specifics (endpoints, property names and defaults).
- Use the DynamoDB setup scripts in `consumer-v3/src/main/setup-dynamodb-tables.sh` or `database/setup-dynamodb-tables.sh` to create required tables before running the service in production mode.
- Run load-tests from `load-tests/` and monitor metrics with the CloudWatch dashboard in `monitoring/`.

Where to go next
- consumer-v3: `consumer-v3/README.md` (build, run, test, config)
- database: `database/README.md` (DynamoDB setup)
- load-tests: `load-tests/README.md` (how to run and customize tests)
- monitoring: `monitoring/README.md` (CloudWatch dashboard import)

License / Course Notes
This repository contains coursework materials. Follow your course rules on collaboration and submission. Contact course staff if unsure about reuse or licensing.

# load-tests

This folder contains sample scripts for load and endurance testing the consumer service.

Contents
- `endurance_test.sh` - Example endurance/load test script. The script invokes a Java client jar located at `target/client-part2-1.0-SNAPSHOT-jar-with-dependencies.jar` and points it at `ws://chatflow-alb-1154142688.us-west-2.elb.amazonaws.com`.

What to know
- The `endurance_test.sh` script expects a pre-built client jar at `target/client-part2-1.0-SNAPSHOT-jar-with-dependencies.jar`. This repository does not include that client project or jar. The script is therefore an example harness and will fail unless you provide the client jar or replace the command with your own load/test client.
- The script loops 60 times and runs the client each iteration, waiting 30 seconds between runs.

How to run (example)
1. Build or obtain a compatible load-test client jar and place it at `load-tests/target/client-part2-1.0-SNAPSHOT-jar-with-dependencies.jar` (or update the script to point to your client).
2. Make sure the server under test is running and reachable from the machine running the tests.
3. Run the endurance script:

```bash
bash load-tests/endurance_test.sh
```

Customizing
- Replace the `java -jar ...` command in the script with any client command you prefer (e.g., a Gatling, k6, or your own Java/Python client).
- Edit the `SERVER_URL` variable at the top of the script to target a different WebSocket host or IP.

Tips
- Run load tests from a separate machine or container to avoid resource contention with the service under test.
- Gradually ramp up load to get stable, meaningful results.


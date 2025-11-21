# monitoring

This folder contains monitoring and observability resources for the project.

Contents
- `cloudwatch-dashboard.json` - A CloudWatch dashboard definition that you can import into AWS CloudWatch.

Usage
1. Open the CloudWatch console in AWS.
2. Navigate to Dashboards and choose Create dashboard (or Import saved dashboard JSON) and paste the contents of `cloudwatch-dashboard.json`.

Notes
- The dashboard references metrics (namespaces, dimensions) produced by the running service and AWS resources. Adjust metric names or dimensions if your deployment differs.
- The consumer application exposes actuator endpoints and Micrometer is present in the `pom.xml`, but exporting metrics to CloudWatch requires additional configuration (Micrometer CloudWatch registry or push mechanism). The provided dashboard assumes the AWS resources/metrics you want to monitor already exist and are emitting metrics into CloudWatch.
- You can extend the dashboard to include alarms or other visualizations as needed.
# load-tests

This folder contains sample scripts for load and endurance testing the consumer service.

Contents
- `endurance_test.sh` - Example endurance/load test script. Edit it to set the target host/port and test parameters.

Quick run
1. Make sure the service is running and reachable from the machine where you run the tests.
2. Edit `load-tests/endurance_test.sh` to configure the target host and any load parameters.
3. Run the script:

```bash
bash load-tests/endurance_test.sh
```

Tips
- Run load tests from a separate machine or container to avoid resource contention with the service.
- Monitor CPU, memory and network while running tests.
- Gradually ramp up the load to avoid sudden spikes that can make results noisy.

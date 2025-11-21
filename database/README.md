# database

This folder includes helper scripts to create and manage DynamoDB resources used by the consumer service.

Contents
- `setup-dynamodb-tables.sh` - Script to create DynamoDB tables required by the application. (Same content is also present at `consumer-v3/src/main/setup-dynamodb-tables.sh`.)
- `launch-rds.sh` - Optional helper for launching/configuring RDS (inspect before use).

What the setup script creates (exact)
- `chatflow-messages` — primary key: `roomId` (HASH), `timestamp` (RANGE). Global secondary indexes: `UserMessagesIndex` (userId, timestamp) and `MessageIdIndex` (messageId).
- `chatflow-room-participation` — primary key: `userId` (HASH), `roomId` (RANGE).
- `chatflow-analytics` — primary key: `metricType` (HASH), `entityId` (RANGE).

Defaults used by the script
- AWS Region: `us-west-2` (can be overridden by exporting `AWS_REGION` environment variable before running).
- Account ID printed for informational purposes: `590183786772`.

Run the script (requires AWS CLI and correct credentials):

```bash
# From repo root
bash database/setup-dynamodb-tables.sh

# Or the copy in consumer-v3
bash consumer-v3/src/main/setup-dynamodb-tables.sh
```

If you want to override region on the command line:

```bash
AWS_REGION=us-east-1 bash database/setup-dynamodb-tables.sh
```

Verify created tables:

```bash
aws dynamodb list-tables --region us-west-2
aws dynamodb describe-table --table-name chatflow-messages --region us-west-2
```

Notes
- Review the script before running. It will silently continue if a table already exists (the script prints "already exists").
- For local development, consider using DynamoDB Local or LocalStack and modify `application.properties` in `consumer-v3` to point at the local endpoint.

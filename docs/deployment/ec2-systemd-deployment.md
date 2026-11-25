# EC2 deployment preparation

This guide prepares an EC2 service. It does not configure AWS, create a service, or deploy the application.

## Requirements

- Ubuntu EC2 with Java 17 and Maven installed for build steps, or build the JAR in CI.
- An EC2 instance profile with least-privilege access to the required S3 bucket, SQS queue, and DynamoDB table. The backend uses the AWS SDK default credentials chain; do not set static AWS keys.
- Private RDS connectivity from the instance, with PostgreSQL TLS trust configured for the RDS CA.
- Redis reachable from the instance.
- `/etc/moldy-market/moldy.env` created from `.env.example`; replace every placeholder. Keep this file outside Git and readable only by root (`0600`).
- A schema comparison against a restored database copy completed before enabling `SPRING_PROFILES_ACTIVE=prod`, as described in [the database migration plan](../production-database-migration-plan.md).

## Build and stage the JAR

Replace `<merged-branch>` with the reviewed branch name. Run the build from the repository root. This project uses the local `shared` Maven module, so install it before building the backend.

```bash
cd /opt/src/moldy-market
git fetch origin
git checkout <merged-branch>
git pull --ff-only origin <merged-branch>
mvn -f shared/pom.xml clean install -DskipTests
mvn clean verify
sudo install -d -o ubuntu -g ubuntu -m 0750 /opt/moldy-market
sudo install -o ubuntu -g ubuntu -m 0640 target/moldyMarket-0.0.1-SNAPSHOT.jar /opt/moldy-market/app.jar
```

For a build made in CI, transfer the verified JAR and skip Maven installation on EC2.

## Create the environment file

```bash
sudo install -d -o root -g ubuntu -m 0750 /etc/moldy-market
sudoedit /etc/moldy-market/moldy.env
sudo chown root:root /etc/moldy-market/moldy.env
sudo chmod 0600 /etc/moldy-market/moldy.env
```

Populate the file from `.env.example` with actual values obtained through the organization's secret-management process. Keep the database URL on the RDS DNS endpoint and preserve `sslmode=verify-full`.

## Install and start systemd unit

Review [the example unit](../../deploy/moldy-market.service.example), then install it only after the database compatibility gate and environment file are ready:

```bash
sudo install -o root -g root -m 0644 deploy/moldy-market.service.example /etc/systemd/system/moldy-market.service
sudo systemctl daemon-reload
sudo systemctl enable --now moldy-market.service
sudo systemctl status --no-pager moldy-market.service
sudo journalctl -u moldy-market.service -n 100 --no-pager
```

The service runs as `ubuntu`, restarts on failure, waits for network availability, and reads secrets from the root-owned external environment file. Never dump `systemctl show ... Environment` or the environment file into logs or support tickets.

## Verify

```bash
sudo systemctl is-active moldy-market.service
curl --fail --silent --show-error http://127.0.0.1:8080/api/v1/categories
```

The categories route is an existing public GET endpoint. The project does not currently include a dedicated health/actuator endpoint. Production Swagger/OpenAPI is disabled; verify its deliberate unavailability instead of using it as a health check. Configure an ALB health-check route only after selecting or adding an explicit health endpoint.

## Operational notes

- Inspect logs with `journalctl -u moldy-market.service`; application logs do not print environment secrets.
- Start with outbox disabled unless its database schema, SQS queue, Lambda trigger, retry, and DLQ setup have been verified. When enabled, set `NOTIFICATIONS_OUTBOX_ENABLED=true` and a real SQS queue URL.
- Configure IAM, VPC security groups, RDS CA trust, Redis, Brevo sender verification, SePay webhook URL/secret, DNS/TLS, and any ALB health check outside this repository.

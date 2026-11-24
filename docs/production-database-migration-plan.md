# Production database migration plan

## Current state

- The backend uses Spring Boot 4.1.0, PostgreSQL, and 43 JPA entity classes. Entities define foreign-key relationships, unique constraints, and indexes, but the repository does not contain a production schema snapshot or a complete schema export.
- The local profile uses Hibernate `update` for development. The production profile uses `none` and disables SQL initialization, so startup cannot silently alter an unknown schema. Hibernate `validate` is intentionally not enabled until an actual RDS schema snapshot has been compared with the entities and the migration baseline is approved.
- Neither Flyway nor Liquibase is configured in `pom.xml`.
- `src/main/resources/db/migration` contains three manually applied SQL scripts. The files are not managed by a migration runner. They cover enum check constraints/indexes, chat read-cursor columns, and a larger demo-integration schema change. The latter alters existing tables and updates dispute participant data, so it needs a restored production copy and reviewed preconditions before use.
- No database connection, migration, or DDL was executed during this preparation.

## Migration inventory and risk

The entity model covers users and authentication, roles/permissions, stores and staff, products/categories, carts, orders and checkout, payments/refunds, appraisal, commission, wallets/escrow/withdrawals, disputes, reviews, vouchers, stored files, chat, pricing, and notification outbox data. Entity annotations describe a significant set of relations, unique constraints, and indexes. Database-level state, current enum values, legacy columns, data quality, and constraint names cannot be inferred reliably from entity annotations alone.

Existing SQL files:

| Script | Scope | Review needed |
| --- | --- | --- |
| `V20261120__checkout_statuses.sql` | Replaces status check constraints and adds a payment index | Confirm existing check names and enum ordinal range |
| `V20261121__chat_read_cursors.sql` | Adds two nullable cursor columns | Confirm table name and column state |
| `V20261123__demo_integration_schema.sql` | Adds voucher/file columns, tables/indexes, and migrates dispute participant data | Restore-test first; verify nulls, duplicates, names, data, lock duration, and all preconditions |

These scripts cannot be applied safely as an initial baseline. The first script drops and recreates constraints, and the third includes an `UPDATE` plus changes to live constraints. They are not an exhaustive schema history.

## Flyway decision

Flyway is a reasonable future migration runner, but it is intentionally not added or enabled here. Adding the runner to this artifact set would auto-discover the existing versioned SQL scripts and attempt to apply them on startup, without a verified schema history or baseline. First obtain a schema-only dump and migration history from the actual RDS database, compare them with all 43 JPA entities and the three scripts, then create a reviewed baseline and a controlled migration rollout.

Spring Boot 4.1.0's dependency-management line is the right place to select a Flyway version rather than pinning one independently. The downloaded Spring Boot 4.1.0 BOM manages Flyway 12.4.0; the current Spring Boot 4.1 documentation has since advanced to 4.1.3 and lists Flyway 12.11.0. PostgreSQL support is a separate Flyway module. Flyway's current Java API documentation states Java 17+ support, while later Flyway majors may raise that minimum. Recheck the exact Boot BOM and Flyway PostgreSQL module when implementing the runner.

## Safe rollout sequence

1. Take and verify an RDS snapshot; restore it into an isolated staging instance.
2. Capture schema-only DDL, PostgreSQL version, installed extensions, current constraints/indexes, and any existing migration ledger.
3. Compare the restored schema against the JPA entities and every SQL script. Resolve duplicates, missing values, enum ordinals, table/column naming, and constraint-name differences.
4. Convert only reviewed changes into ordered migration files. Do not mark existing schema as baselined until the comparison is approved.
5. Run Flyway `validate` and the migration plan against the restored copy, then test the app with Hibernate `validate`.
6. Only after staging succeeds, schedule a production change window with a fresh snapshot and an explicit operator-run migration. Keep app startup from applying unreviewed migrations.

## Connection security

Production profile forces PostgreSQL `sslmode=verify-full`. The production JDBC URL must use the RDS DNS endpoint and the EC2 JVM must trust the current AWS RDS CA chain. Keep credentials outside the repository. No RDS hostname, password, or security group was inspected or changed.

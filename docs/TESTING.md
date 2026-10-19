# WatchHarbor Testing Guide

## 1. Purpose

This document describes how to execute and verify the automated tests and manual checks for WatchHarbor's Monitor Service and Monitoring Worker.

The goal is to make test results reproducible and to distinguish verified behavior from functionality that has not yet been verified.

## 2. Verified Environment

| Component           | Verified configuration             |
| ------------------- | ---------------------------------- |
| Operating system    | Windows development environment    |
| Shell               | Git Bash                           |
| Java                | Java 21                            |
| Spring Boot         | 4.1.1                              |
| Database            | PostgreSQL                         |
| Database migrations | Flyway                             |
| Backend build tool  | Maven Wrapper                      |
| Automated testing   | JUnit 5, Mockito, Spring Boot Test |

Record the exact Java and PostgreSQL patch versions, operating system build, Maven version, and Git commit for each formal test report. Those exact values were not captured in the available test evidence.

## 3. Automated Test Results

The following test runs completed successfully.

| Component         | Command               | Tests | Failures | Errors | Skipped | Result        |
| ----------------- | --------------------- | ----: | -------: | -----: | ------: | ------------- |
| Monitor Service   | `./mvnw clean verify` |    87 |        0 |      0 |       0 | BUILD SUCCESS |
| Monitoring Worker | `./mvnw clean verify` |    96 |        0 |      0 |       0 | BUILD SUCCESS |

### 3.1 GitHub Actions Verification

The backend CI workflow is located at
`.github/workflows/backend-ci.yml`.

It runs separate verification jobs for:

- Monitor Service
- Monitoring Worker

The workflow is triggered by pushes to `main`, pull requests targeting
`main`, and manual dispatch.

**Recorded CI result:** The workflow completed successfully for commit
`9712c6d` (`ci: add backend verification workflow`).

This confirms the overall workflow succeeded for that commit. Check the
individual job logs in GitHub Actions for detailed results.

[View GitHub Actions runs](https://github.com/vivekkushwahaofficial/WatchHarbor/actions).

The recorded local test counts above are separate from the CI result.

These results were observed during local verification on 19 October 2026. They describe those test runs, not a guarantee that every future commit will pass.

### 3.2 Monitor Service

From the repository root, run:

```bash
cd services/monitor-service
./mvnw clean verify
```

The `clean` goal removes previous build output. The `verify` goal executes the configured Maven verification lifecycle, including compilation and tests.

**Recorded result:** 87 tests, zero failures, zero errors, zero skipped, and `BUILD SUCCESS`.

### 3.3 Monitoring Worker

From the repository root, run:

```bash
cd services/monitoring-worker
./mvnw clean verify
```

**Recorded result:** 96 tests, zero failures, zero errors, zero skipped, and `BUILD SUCCESS`.

The worker test suite includes coverage for job claiming, lease recovery, HTTP checking, unsafe destination rejection, DNS-related behavior, the Monitor Service client, database migrations, and job processing. Individual test names and detailed coverage percentages should be captured from the actual reports rather than inferred from the test count.

## 4. Database and Migration Verification

The automated database-backed tests used the local PostgreSQL test database `watchharbor_test`.

Flyway migration validation and application startup were included in the recorded test runs.

The Monitoring Worker owns its database objects in the `monitoring_worker` schema. Its queue and check-result tables are separate from the Monitor Service's monitor data.

For repeatable testing:

- Use a dedicated test database.
- Keep test credentials outside version control.
- Never point destructive tests at a development or production database.
- Record the Flyway schema version used by each formal test run.
- Verify database constraints and transaction behavior with integration tests.

The available evidence confirms successful migration validation, but does not establish that every database constraint or transaction failure scenario has been tested.

## 5. Manual End-to-End Verification

### 5.1 Objective

Verify that a real queued job can be claimed by the Monitoring Worker, checked against an HTTP endpoint, and recorded in PostgreSQL.

### 5.2 Preconditions

- PostgreSQL is running.
- The Monitor Service is available at `http://localhost:8081`.
- The Monitoring Worker is available at `http://localhost:8082`.
- The existing monitor has ID `1` and points to `https://example.com`.
- The test environment has network access to the configured endpoint.

### 5.3 Procedure

**Step 1 — Check service health**

```bash
curl -i http://localhost:8081/actuator/health
curl -i http://localhost:8082/actuator/health
```

**Recorded result:** Both endpoints returned HTTP 200 with health status `UP`.

**Step 2 — Insert a due job**

Connect to the local `watchharbor_monitor` PostgreSQL database using a PostgreSQL client. Execute:

```sql
INSERT INTO monitoring_worker.monitoring_jobs
    (monitor_id, due_at)
VALUES
    (1, NOW())
RETURNING id, monitor_id, status, due_at;
```

**Recorded result:** The inserted job had ID `1`, monitor ID `1`, and initial status `PENDING`.

**Step 3 — Verify job completion**

After allowing the worker to process the job, query:

```sql
SELECT
    id,
    monitor_id,
    status,
    attempt_count,
    last_error_code
FROM monitoring_worker.monitoring_jobs
WHERE id = 1;
```

**Recorded result:** The job reached `SUCCEEDED` with `attempt_count = 1` and no recorded error code.

**Step 4 — Verify the check result**

```sql
SELECT
    job_id,
    monitor_id,
    attempt_count,
    outcome,
    http_status,
    response_time_ms,
    error_code,
    checked_at
FROM monitoring_worker.monitoring_check_results
WHERE job_id = 1;
```

**Recorded result:** One result was stored for job `1`, with outcome `SUCCESS`, HTTP status `200`, response time `377 ms`, and no error code.

### 5.4 Interpretation

This manual test verified the following path:

1. A due job was inserted into the worker-owned PostgreSQL queue.
2. The worker claimed and processed the job.
3. The worker requested monitor configuration from the Monitor Service.
4. The worker performed an HTTP check.
5. The job was marked successful.
6. The check result was persisted in PostgreSQL.

This is evidence of one successful end-to-end execution. It does not prove recurring scheduling, high concurrency, production availability, or successful handling of every failure mode.

## 6. Keep-Alive Assistance Test Plan (Planned)

The dedicated keep-alive configuration and scheduling workflow is a product goal but is not claimed as implemented yet. When implemented, add and run tests for:

- Creating configuration for an authorized HTTP/HTTPS target with a permitted interval.
- Rejecting invalid URLs, unsupported schemes, and intervals outside configured/provider-permitted limits.
- Enabling, pausing, and resuming schedules; checking next execution time.
- Distinguishing keep-alive jobs/results from ordinary uptime checks.
- Idempotent scheduling, duplicate claim prevention, and duplicate result prevention.
- HTTP success, non-success status, timeout, unreachable target, and bounded retries.
- Recording execution time, response time, outcome/status, and failure category.
- Worker restart, expired leases, and recovery of due jobs.
- Applying SSRF, DNS rebinding, redirect, response-size, timeout, and concurrency controls equally to keep-alive targets.
- Respecting configured request rates and provider terms; never implementing restriction evasion.
- Documenting that scheduled jobs cannot run while WatchHarbor's scheduler/worker is offline or asleep.

Do not describe Keep-Alive Assistance as implemented until its code and applicable tests have actually been executed and reviewed.

## 7. Security and Failure-Handling Tests

The worker test suite includes tests related to unsafe destinations, DNS behavior, response handling, lease recovery, and processing failures.

Future test reports should identify the exact test classes and methods for each security property. In particular, public deployment requires evidence for:

- Blocking loopback, private, link-local, reserved, and metadata-service destinations.
- Validating resolved IP addresses and redirect destinations.
- Resisting DNS rebinding.
- Enforcing connection and response time limits.
- Limiting response sizes and concurrent requests.
- Handling expired claims and duplicate job processing safely.
- Applying bounded retries and preventing duplicate result records.

A passing test suite must not be interpreted as proof that the worker is secure against every SSRF or distributed-system failure scenario.

## 8. Test Reports and Evidence

Maven test reports are generated under each service's `target/surefire-reports/` directory when Surefire is configured for the test suite.

Useful evidence includes:

- The Git commit SHA tested.
- The exact command executed.
- Java, Maven, Spring Boot, and PostgreSQL versions.
- Total tests, failures, errors, and skipped tests.
- The final Maven build result.
- Relevant integration-test logs.
- Database migration version.
- Manual test SQL and observed results.
- Any warnings or environmental limitations.

Do not commit secrets, `.env` files, passwords, access tokens, or sensitive production logs.

## 9. Current Limitations

The recorded verification does not yet establish:

- Automated CI execution on every pull request.
- Code coverage percentages or coverage thresholds.
- Load, stress, or long-duration reliability results.
- Multi-worker concurrency under a controlled integration test.
- A complete recurring scheduling workflow.
- Production deployment readiness.
- A completed Keep-Alive Assistance configuration and lifecycle workflow.

These are future verification tasks, not completed claims.

## 10. Reproducibility Checklist

Before reporting a new test run:

- [ ] Record the commit SHA.
- [ ] Record Java, Maven, Spring Boot, and PostgreSQL versions.
- [ ] Confirm the intended test database is configured.
- [ ] Run `./mvnw clean verify` for each changed service.
- [ ] Record the exact test totals and Maven build outcome.
- [ ] Review test reports and investigate failures.
- [ ] Record manual checks separately from automated tests.
- [ ] Document warnings and tests not performed.
- [ ] Update this guide when test commands or architecture change.

# WatchHarbor

**An open-source Uptime Monitoring + Keep-Alive Assistance platform.**

WatchHarbor is being developed to help developers monitor HTTP/HTTPS endpoints and, where their hosting provider permits it, schedule bounded requests to their own backend endpoints to help reduce inactivity-based sleeping. The platform is built incrementally with Java, Spring Boot, PostgreSQL, and a local-first ₹0 development budget.

> **Development status:** Early development. The Monitor Service and Monitoring Worker have been implemented and locally verified. Keep-Alive Assistance is a core product goal, but its dedicated configuration, lifecycle, scheduling, and history workflow are planned until implemented and tested. Authentication, the frontend dashboard, incident management, and notifications also remain planned work.

## Core Capabilities

### 1. Uptime Monitoring
- Manage monitor configuration and lifecycle.
- Check HTTP/HTTPS endpoints and measure response times.
- Build toward uptime history, incident detection, and outage/recovery notifications.

### 2. Keep-Alive Assistance
- Plan scheduled, bounded requests to endpoints the user is authorized to operate.
- Support enabling, pausing, and resuming schedules.
- Record execution time, response time, outcome, and failure category.
- Distinguish keep-alive jobs from ordinary health checks.

Keep-alive requests are **best-effort assistance**. They do not guarantee that a hosted application stays awake, bypass provider restrictions, or override quotas. The provider must permit the traffic. WatchHarbor itself must be running to execute scheduled requests.

## Features and Status

### Implemented and locally verified
- Monitor Service for monitor configuration and lifecycle operations.
- Monitoring Worker for processing queued monitoring jobs.
- PostgreSQL persistence and Flyway database migrations.
- HTTP/HTTPS destination validation and protections against unsafe destinations.
- Automated backend tests using JUnit 5, Mockito, and Spring Boot Test.
- GitHub Actions workflow for backend verification.

### Planned
- Keep-Alive Assistance configuration, schedule lifecycle, and execution history.
- User registration, authentication, authorization, and resource ownership.
- Uptime history and statistics.
- Incident detection and recovery tracking.
- Outage and recovery notifications.
- API Gateway.
- React and TypeScript dashboard.
- Additional integration, resilience, and security testing.

Features will be marked as implemented only after they have been developed and verified.

## Technology Stack

| Area | Technologies |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1.x |
| APIs | Spring Web, REST |
| Persistence | Spring Data JPA, PostgreSQL |
| Database migrations | Flyway |
| Testing | JUnit 5, Mockito, Spring Boot Test |
| Build | Maven Wrapper |
| CI | GitHub Actions |
| Planned frontend | React, TypeScript, Vite, Tailwind CSS |

## Architecture

The backend is being built as independently maintainable services.

- **Monitor Service:** Owns monitor configuration and related data.
- **Monitoring Worker:** Claims and processes queued monitoring jobs, performs endpoint checks, and stores worker-owned results. It is intended to handle ordinary health-check jobs and keep-alive jobs as distinct purposes.
- **Future services:** Auth, History, Incident, Notification, and API Gateway components will be introduced incrementally when needed.

Each service owns its data and schema. The worker must not directly modify the Monitor Service's tables. API contracts and persistence ownership must be documented before cross-service workflows are implemented.

## Local Development

WatchHarbor is designed to run locally using free and open-source tools without paid hosting or APIs.

### Prerequisites
- Git
- Java 21
- PostgreSQL
- A terminal such as Git Bash on Windows

### Run backend verification

From the repository root, run each service's verification separately.

**Monitor Service**
```bash
cd services/monitor-service
./mvnw --batch-mode clean verify
```

**Monitoring Worker**
```bash
cd services/monitoring-worker
./mvnw --batch-mode clean verify
```

On Windows, run these commands in Git Bash. Each service includes its own Maven Wrapper. Database-backed tests require the test database and environment variables documented in the relevant test configuration.

For detailed test instructions, recorded results, and manual verification steps, see [docs/TESTING.md](docs/TESTING.md).

## Continuous Integration

The workflow at [`.github/workflows/backend-ci.yml`](.github/workflows/backend-ci.yml) runs backend verification for pushes to `main` and pull requests targeting `main`. It can also be started manually.

The workflow currently contains separate jobs for the Monitor Service and Monitoring Worker.

[View GitHub Actions runs](https://github.com/vivekkushwahaofficial/WatchHarbor/actions).

## Security

Security is a core development requirement. Before public deployment, the platform must implement and verify authentication, authorization, resource ownership checks, rate limiting, and internal-service access controls.

All user-provided monitoring and keep-alive URLs must receive the same SSRF protections: only HTTP/HTTPS, safe DNS/IP validation, protection against DNS rebinding, safe redirect handling, timeouts, response-size limits, and bounded concurrency. Keep-alive must be limited to authorized targets and permitted provider usage.

Do not commit secrets, passwords, or real environment files. See [SECURITY.md](SECURITY.md).

## Contributing

Contributions should follow the project's coding, testing, and security standards. See [CONTRIBUTING.md](CONTRIBUTING.md) for the development workflow.

## License

WatchHarbor is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

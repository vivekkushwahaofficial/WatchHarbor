# WatchHarbor

**An open-source website and API uptime monitoring platform.**

WatchHarbor is a monitoring platform built with Java, Spring Boot, and PostgreSQL. Its goal is to check HTTP/HTTPS endpoints, record monitoring results, track uptime, and support incident management and notifications.

The project is being developed incrementally, with a focus on security, reliable background processing, automated testing, and a ₹0 local-development budget.

> **Development status:** Early development. The Monitor Service and Monitoring Worker have been implemented and locally verified. Authentication, the frontend dashboard, incident management, and notifications remain planned work.

## Features

### Implemented

- Monitor Service for monitor configuration and lifecycle operations.
- Monitoring Worker for processing queued monitoring jobs.
- PostgreSQL persistence and Flyway database migrations.
- HTTP/HTTPS destination validation and protections against unsafe destinations.
- Automated backend tests using JUnit 5, Mockito, and Spring Boot Test.
- GitHub Actions workflow for backend verification.

### Planned

- User registration, authentication, and authorization.
- Uptime history and statistics.
- Incident detection and recovery tracking.
- Outage and recovery notifications.
- API Gateway.
- React and TypeScript dashboard.
- Additional integration, resilience, and security testing.

Features will be marked as implemented only after they have been developed and verified.

## Technology Stack

| Area                | Technologies                          |
| ------------------- | ------------------------------------- |
| Backend             | Java 21, Spring Boot 4.1.x            |
| APIs                | Spring Web, REST                      |
| Persistence         | Spring Data JPA, PostgreSQL           |
| Database migrations | Flyway                                |
| Testing             | JUnit 5, Mockito, Spring Boot Test    |
| Build               | Maven Wrapper                         |
| CI                  | GitHub Actions                        |
| Planned frontend    | React, TypeScript, Vite, Tailwind CSS |

## Architecture

The backend is being built as independently maintainable services.

- **Monitor Service:** Owns monitor configuration and related data.
- **Monitoring Worker:** Claims and processes monitoring jobs, performs endpoint checks, and stores worker-owned results.

The services use separate database ownership boundaries. The worker must not directly modify the Monitor Service's tables.

Other services, including authentication, history, incidents, notifications, and the API Gateway, are planned for later milestones.

## Local Development

WatchHarbor is designed to run locally using free and open-source tools without requiring paid hosting or APIs.

### Prerequisites

- Git
- Java 21
- PostgreSQL
- A terminal such as Git Bash on Windows

### Run backend verification

From the repository root, run each service's verification separately:

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

On Windows, run these commands in Git Bash. Each service includes its own Maven Wrapper.

Database-backed tests require the test database and environment variables documented in the relevant test configuration.

For detailed test instructions, recorded results, and manual verification steps, see [docs/TESTING.md](docs/TESTING.md).

## Continuous Integration

The workflow at [`.github/workflows/backend-ci.yml`](.github/workflows/backend-ci.yml) runs backend verification for pushes to `main` and pull requests targeting `main`. It can also be started manually.

The workflow currently contains separate jobs for the Monitor Service and Monitoring Worker.

[View GitHub Actions runs](https://github.com/vivekkushwahaofficial/WatchHarbor/actions).

## Security

Security is a core development requirement. Before public deployment, the platform must implement and verify appropriate authentication, authorization, resource ownership checks, rate limiting, and internal-service access controls.

The monitoring worker must protect against server-side request forgery (SSRF), unsafe redirects, DNS rebinding, private or reserved network destinations, excessive response sizes, and unbounded requests.

Do not commit secrets, passwords, or real environment files.

See [SECURITY.md](SECURITY.md) for security reporting guidance.

## Contributing

Contributions should follow the project's coding, testing, and security standards. See [CONTRIBUTING.md](CONTRIBUTING.md) for the development workflow.

## License

WatchHarbor is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

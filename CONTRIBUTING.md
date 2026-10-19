# Contributing to WatchHarbor

Thank you for your interest in contributing to WatchHarbor.

WatchHarbor is an open-source website and API uptime monitoring platform. Contributions should prioritize correctness, security, maintainability, clear documentation, and local development without paid services.

## Before Contributing

1. Read the [README](README.md) for the project overview.
2. Review [PROJECT_INSTRUCTIONS.md](PROJECT_INSTRUCTIONS.md) for architecture and development standards.
3. Review [SECURITY.md](SECURITY.md) before reporting or working on security-sensitive issues.
4. Check existing issues and pull requests to avoid duplicating work.

## Development Environment

The backend uses Java 21, Spring Boot 4.1.x, PostgreSQL, Flyway, and the Maven Wrapper.

Install Git, Java 21, and PostgreSQL before running database-backed tests. Follow the test database configuration in the relevant service.

## Backend Verification

Run verification separately for each implemented backend service.

### Monitor Service

```bash
cd services/monitor-service
./mvnw --batch-mode clean verify
```

### Monitoring Worker

```bash
cd services/monitoring-worker
./mvnw --batch-mode clean verify
```

For additional details, see [docs/TESTING.md](docs/TESTING.md).

Do not claim that tests pass unless you have run them and checked the actual results.

## Coding Standards

- Use constructor injection and keep controllers thin.
- Separate API controllers, services, repositories, DTOs, entities, and configuration.
- Validate inputs and return appropriate HTTP status codes.
- Use Flyway migrations for database schema changes.
- Keep each service responsible for its own data.
- Use timeouts, bounded retries, and idempotent processing where appropriate.
- Add tests for meaningful behavior, validation, database changes, and failure scenarios.
- Avoid unnecessary dependencies and unrelated refactoring.
- Never commit secrets or real environment files.

## Security-Sensitive Changes

Changes to monitoring destinations, DNS resolution, redirects, HTTP clients, authentication, or internal service communication require careful security review and appropriate tests.

Do not weaken SSRF protections or bypass destination validation to make a test pass.

## Pull Requests

Before submitting a pull request:

1. Run the relevant Maven verification command.
2. Review your changes with `git diff`.
3. Check formatting with `git diff --check`.
4. Update documentation when behavior or setup changes.
5. Describe the change, design decisions, tests run, and known limitations.
6. Never include credentials, secrets, or unrelated generated files.

GitHub Actions runs backend verification for pull requests targeting `main`.

## Scope

WatchHarbor is developed incrementally. Avoid adding unimplemented services, paid infrastructure, or large architectural changes without first discussing the design and scope.

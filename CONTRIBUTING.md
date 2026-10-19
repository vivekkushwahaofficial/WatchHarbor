# Contributing to WatchHarbor

Thanks for contributing to WatchHarbor, an open-source **Uptime Monitoring + Keep-Alive Assistance** platform. Contributions should prioritize correctness, security, maintainability, documentation, and local development at ₹0.

## Before Contributing
1. Read [README.md](README.md) for the current project goals and status.
2. Review [PROJECT_INSTRUCTIONS.md](PROJECT_INSTRUCTIONS.md) for architecture and development standards.
3. Read [SECURITY.md](SECURITY.md) before handling security-sensitive issues.
4. Check existing issues and pull requests to avoid duplicate work.

## Development Environment
The current backend uses Java 21, Spring Boot 4.1.x, PostgreSQL, Flyway, and Maven Wrapper. Install Git, Java 21, and PostgreSQL before running database-backed tests.

## Backend Verification

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

See [docs/TESTING.md](docs/TESTING.md) for recorded evidence and additional test requirements. Do not claim tests pass unless you ran them and checked the results.

## Engineering Standards
- Use constructor injection and thin controllers.
- Separate controllers, services, repositories, DTOs, entities, and configuration.
- Validate inputs and return appropriate HTTP status codes.
- Use Flyway for schema changes and keep ownership boundaries clear.
- Use timeouts, bounded retries, and idempotent processing where appropriate.
- Add tests for meaningful behavior, validation, persistence, and failure scenarios.
- Avoid unnecessary dependencies and unrelated refactoring.
- Never commit secrets or real environment files.

## Uptime and Keep-Alive Features
Keep ordinary uptime checks distinct from keep-alive jobs. Keep-alive requests must target endpoints the user is authorized to operate and must respect hosting-provider terms, quotas, and application limits. Keep-alive is best-effort and must not be described as guaranteed availability or provider-restriction evasion.

Changes to URL validation, DNS resolution, redirects, HTTP clients, scheduling, job claiming, authentication, or service communication require appropriate tests and security review.

## Pull Requests
Before opening a pull request:
1. Run relevant Maven verification.
2. Review changes using `git diff` and run `git diff --check`.
3. Update documentation when behavior or setup changes.
4. Describe design decisions, tests actually run, and known limitations.
5. Exclude credentials, secrets, and unrelated generated files.

GitHub Actions runs backend verification for pull requests targeting `main`.

## Scope
Build incrementally. Do not add empty services, paid infrastructure, or large architectural changes without discussing the design first.

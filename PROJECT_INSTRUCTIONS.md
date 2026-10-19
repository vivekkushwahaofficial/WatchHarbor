# WatchHarbor — Project Instructions

## Goal
Build **WatchHarbor**, an original open-source **Uptime Monitoring + Keep-Alive Assistance** platform in the separate `watchharbor` repository. Keep it separate from PeerNexus and use original code/assets.

Core goals: monitor HTTP/HTTPS endpoints, response times, uptime history, incidents, and alerts; schedule bounded requests to users' own authorized backend endpoints where provider terms permit; enable/pause/resume schedules and record execution history. Keep-alive is best-effort and cannot guarantee availability or override provider limits. Never claim planned features are implemented before testing.

## Teaching Style
I am a student familiar with Java, Spring Boot, React, PostgreSQL, REST APIs, and Git. Act as my software architect, backend engineer, code reviewer, DevOps mentor, and patient instructor. Use simple English.

For each task: explain goal/design; identify exact files; provide complete relevant code and explain key choices; give Windows/Git Bash commands, tests, expected results, and troubleshooting; provide a checklist and summarize changes, tests, limitations, and next steps. Work incrementally, preserve working code, avoid unrelated refactoring, and wait for milestone verification. Never invent changes, outputs, test results, or deployment status.

## ₹0 Budget
- Prefer free/open-source tools and local development.
- Never require paid APIs, subscriptions, domains, hosting, managed services, or email.
- Verify current free-tier terms and explain quotas, sleep, persistence, and limitations.
- Never enable billing or add paid infrastructure without explicit approval.
- Keep the app runnable locally without cloud services; email is optional locally.
- Never promise unlimited capacity or guaranteed uptime from free hosting.

## Technology
- Frontend: React, TypeScript, Vite, Tailwind CSS, React Router.
- Backend: Java 21, established Spring Boot 4.1.x version, Spring Web, Spring Data JPA, Spring Security, Bean Validation, Actuator, Flyway, Maven Wrapper.
- Database: PostgreSQL.
- Communication/jobs: REST initially; PostgreSQL-backed job queue first; consider a locally runnable message broker later.
- Testing/tooling: JUnit 5, Mockito, Spring Boot Test, Testcontainers where practical, React testing tools, Git, GitHub Actions, Docker Compose, curl/Postman.
Respect established dependency versions unless a change is explained. Avoid needless infrastructure.

## Architecture and Ownership
Build independently buildable services incrementally:
1. **Monitor Service:** monitor configuration, CRUD, validation, lifecycle, and scheduling metadata.
2. **Monitoring Worker:** safely claims and executes health-check and keep-alive jobs; records results and prevents duplicate processing.
3. **Auth Service:** registration, authentication, password hashing, identity, and authorization.
4. **History Service:** paginated check/keep-alive history, response times, and documented uptime statistics.
5. **Incident Service:** confirmed outage and recovery tracking.
6. **Notification Service:** outage/recovery alerts, bounded retries, deduplication, and delivery outcomes.
7. **API Gateway:** added when useful.
8. **React frontend:** accounts, monitors, keep-alive controls/history, status, incidents, and notification settings.

Start from the existing Monitor Service and PostgreSQL implementation. Each service owns its data/schema; never modify another service's tables directly. Document API/event contracts and ownership. Use timeouts, bounded retries/backoff, idempotency, useful correlation IDs, and recovery tests. Avoid circular dependencies and needless distributed transactions.

## Workflows
Keep these purposes separate:
- **Uptime monitoring** measures availability and response time and informs history, incidents, and alerts.
- **Keep-Alive Assistance** sends scheduled requests to user-authorized endpoints to help reduce inactivity-based sleeping where permitted. Do not evade provider restrictions.

Plan target URL, permitted interval, enabled/paused state, next execution, and execution history. Handle duplicate jobs, timeouts, unreachable targets, worker restarts, and scheduling failures. WatchHarbor itself must be running to execute jobs.

## Security
Before public deployment:
- Implement authentication, authorization, ownership checks, rate limits, and narrow CORS.
- Protect health-check and keep-alive requests against SSRF.
- Allow only HTTP/HTTPS; block loopback, private, link-local, reserved, and cloud-metadata destinations.
- Validate resolved IPs and defend against DNS rebinding.
- Disable redirects initially or validate every redirect destination.
- Apply connection/total timeouts, response-size and concurrency limits.
- Do not rely only on string-based URL validation.
- Restrict internal service endpoints; never expose secrets, plaintext passwords, stack traces, or internal infrastructure details.
- Keep real `.env` files and credentials out of Git; commit only a secret-free `.env.example`.

## Code Quality and Testing
Use constructor injection, thin controllers, and focused services/repositories/DTOs/entities/configuration. Validate input; return consistent errors and correct HTTP status codes. Avoid duplicated logic, magic numbers, giant classes, unnecessary abstractions, and swallowed exceptions. Use Flyway, constraints/indexes, timezone-aware timestamps, and transactions as needed; do not casually edit applied migrations.

Test business logic, API validation, persistence, authorization, service communication, timeouts, unreachable targets, scheduler/worker failures, duplicate jobs, retries, idempotency, concurrent claiming, restart recovery, and frontend loading/empty/error states as relevant. Never claim tests passed unless actually run.

## Repository and Documentation
Maintain relevant `frontend/`, `services/`, `infrastructure/`, `docs/`, `.github/workflows/`, `docker-compose.yml`, `.env.example`, `.gitignore`, `LICENSE`, `README.md`, and this file. Document setup, architecture/data flows, API contracts, service/data ownership, tests, environment variables, security, limitations, contributions, and useful decisions.

Use meaningful commits and CI. Never force-push or run destructive Git commands without explaining the impact and obtaining approval.

## Roadmap
1. Repository and architecture docs.
2. Monitor Service startup; PostgreSQL/Flyway; monitor creation, validation, listing, lifecycle, and tests.
3. Secure HTTP checker and result persistence.
4. Scheduler and reliable job claiming.
5. Keep-Alive Assistance configuration, distinct job purpose, enable/pause/resume, permitted intervals, execution history, provider-limit messaging, and tests.
6. Auth Service and ownership enforcement.
7. History Service and contracts.
8. Incident Service.
9. Notification Service and optional email.
10. API Gateway.
11. React dashboard including keep-alive controls/history.
12. Docker Compose, CI, integration/security/failure-recovery tests.
13. Evaluate zero-cost hosting and document limitations.

Verify each milestone before proceeding; explain roadmap changes first.

## Definition of Success
WatchHarbor should be secure, tested, documented, useful, and locally runnable at ₹0. It should demonstrate React/TypeScript, Java/Spring Boot, PostgreSQL, meaningful service boundaries, safe uptime checks, Keep-Alive Assistance where permitted, execution/history tracking, authentication/ownership, incident/notification workflows, resilient scheduling, CI, and reproducible setup.

Always prioritize correctness, security, learning, maintainability, and the ₹0 budget over speed or code volume.


## Current Status and Evidence

### Completed and locally verified (historical evidence)
- Monitor Service implementation and automated verification.
- Monitoring Worker implementation and automated verification.
- PostgreSQL persistence and Flyway migrations for the implemented services.
- Backend GitHub Actions workflow for the Monitor Service and Monitoring Worker.

Recorded local runs: Monitor Service — 87 tests, zero failures/errors/skipped; Monitoring Worker — 96 tests, zero failures/errors/skipped. These results describe the recorded runs only; verify every future change independently. Check GitHub Actions for the current status of each commit.

### Planned
Keep-Alive Assistance configuration and scheduling, authentication/ownership, history service, incidents, notifications, API Gateway, React dashboard, and additional integration/security/recovery testing. Update status only when implementation and verification evidence exist.

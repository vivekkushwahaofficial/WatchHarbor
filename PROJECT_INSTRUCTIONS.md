# WatchHarbor — Project Instructions

## Project Goal

Build an original, open-source website and API uptime monitoring platform.

## Technology Stack

- Java 21
- Spring Boot 4.1.x
- Spring Web, Spring Data JPA, Spring Security
- PostgreSQL and Flyway
- React, TypeScript, Vite, and Tailwind CSS
- Maven Wrapper
- JUnit 5 and Mockito
- GitHub Actions

## Budget

The development budget is ₹0. Prioritize local development and free, open-source tools. Docker is optional during the initial milestones. Never introduce paid infrastructure without explicit approval.

## Architecture

Implement services incrementally:

1. Monitor Service
2. Monitoring Worker
3. Auth Service
4. History Service
5. Incident Service
6. Notification Service
7. API Gateway
8. React Frontend

Start with the Monitor Service. Do not generate every service at once.

## Engineering Standards

- Use constructor injection and thin controllers.
- Separate controllers, services, repositories, DTOs, and entities.
- Validate inputs and return appropriate HTTP status codes.
- Use Flyway migrations for database changes.
- Write automated tests for meaningful features.
- Never claim tests passed unless they were executed.
- Preserve working code and avoid unrelated refactoring.

## Security

Before public deployment, implement authentication, authorization, resource ownership checks, and appropriate rate limits.

The monitoring worker must defend against SSRF by validating resolved IP addresses, blocking unsafe destinations, controlling redirects, and enforcing timeouts and response-size limits.

Never commit secrets or real environment files.

## Git Workflow

- Use meaningful commits and descriptive branches.
- Never force-push or perform destructive Git operations without explaining the impact and receiving approval.
- Check repository state before pushing.
- Do not overwrite existing GitHub content without reviewing it first.

## Development Workflow

For each milestone:

1. Explain the objective.
2. Identify files to create or modify.
3. Implement one small feature.
4. Explain the design.
5. Run or provide verification steps.
6. Diagnose actual errors.
7. Summarize changes and limitations.
8. Wait for verification before proceeding.

## Current Status

### Completed and verified locally

- Monitor Service implementation and automated verification.
- Monitoring Worker implementation and automated verification.
- PostgreSQL persistence and Flyway migrations for the implemented services.
- Backend GitHub Actions workflow for the Monitor Service and Monitoring Worker.
- Testing documentation in `docs/TESTING.md`.

The recorded local test results are:

- Monitor Service: 87 tests, zero failures, zero errors, zero skipped.
- Monitoring Worker: 96 tests, zero failures, zero errors, zero skipped.

These results describe the recorded local runs. Future changes must be verified independently.

The Backend CI workflow has also completed successfully for commit `9712c6d`. Check GitHub Actions for the status of subsequent runs.

### Planned

- Authentication and authorization.
- History and uptime statistics.
- Incident management.
- Notifications.
- API Gateway.
- React dashboard.
- Additional integration, resilience, and security verification.

Update this section when a milestone has been implemented and its verification evidence is available.

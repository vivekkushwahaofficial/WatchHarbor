# WatchHarbor

WatchHarbor is an open-source website and API uptime monitoring
platform built with Java, Spring Boot, PostgreSQL, React, and TypeScript.

## Project Goals

- Monitor HTTP and HTTPS endpoints.
- Track availability and response times.
- Maintain uptime history.
- Track incidents and recovery.
- Support outage and recovery notifications.

## Technology Stack

### Backend
- Java 21
- Spring Boot 3.x
- Spring Web
- Spring Data JPA
- Spring Security
- PostgreSQL
- Flyway
- Maven

### Frontend
- React
- TypeScript
- Vite
- Tailwind CSS

### Development Tools
- Git and GitHub
- JUnit 5 and Mockito
- Docker Compose (optional for initial development)

## Development Status

Project initialization is in progress.

Features will be marked as implemented only after they have
been developed and verified.

## Local Development

The project is designed to run locally without paid services.
Docker is not required for the initial development milestones.

Setup instructions will be added as the application components
are implemented.

## Security

The monitoring worker will require protection against server-side
request forgery (SSRF), unsafe redirects, and access to internal
network destinations before public deployment.

## License

This project is licensed under the Apache License 2.0.
See the [LICENSE](LICENSE) file for details.

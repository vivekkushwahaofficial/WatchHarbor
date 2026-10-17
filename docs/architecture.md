# WatchHarbor Architecture

## 1. Overview

WatchHarbor monitors HTTP and HTTPS endpoints, records check
results, tracks incidents, and supports notifications.

## 2. Initial Architecture

The first implementation will be the Monitor Service.

Responsibilities:
- Accept monitor configuration.
- Validate request data.
- Persist monitor metadata.
- Expose versioned REST APIs.

## 3. Planned Components

1. Monitor Service
2. Auth Service
3. Monitoring Worker
4. History Service
5. Incident Service
6. Notification Service
7. API Gateway
8. React Frontend

Components will be implemented incrementally.

## 4. Data Ownership

Each service will own its data.

Services will communicate through documented REST APIs initially.
A PostgreSQL-backed job queue will be introduced for monitoring work.

## 5. Monitoring Workflow

1. A user submits a monitor configuration.
2. The Monitor Service validates and saves it.
3. A scheduler identifies due checks.
4. The worker safely claims and executes checks.
5. Check results are persisted through a documented contract.
6. Incident processing evaluates failures and recovery.
7. Notification processing handles alerts.
8. The frontend displays monitoring information.

## 6. Security

Before public deployment:
- Implement authentication and authorization.
- Enforce resource ownership.
- Restrict outbound monitoring requests.
- Validate DNS resolutions and redirect destinations.
- Apply request timeouts and response-size limits.
- Keep credentials and secrets out of Git.

## 7. Development Constraints

- Budget: ₹0.
- Local development comes first.
- Docker is optional during initial development.
- Tests must be executed before reporting them as passed.
- Planned components must not be described as implemented.

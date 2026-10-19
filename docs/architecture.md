# WatchHarbor Architecture

## 1. Product Goal

WatchHarbor has two related but distinct capabilities:

- **Uptime monitoring:** measures the availability and response time of HTTP/HTTPS endpoints and provides evidence for history, incidents, and alerts.
- **Keep-Alive Assistance:** schedules bounded requests to endpoints the user is authorized to operate, where hosting-provider terms permit the traffic, to help reduce inactivity-based sleeping.

Keep-alive is best-effort. It cannot guarantee that a service stays awake or override provider terms, quotas, or restrictions. WatchHarbor itself must be running for scheduled work to execute.

## 2. Current Architecture

The existing implementation includes:

- **Monitor Service:** stores and manages monitor configuration and lifecycle.
- **Monitoring Worker:** claims queued jobs, safely checks HTTP/HTTPS targets, and records worker-owned results.
- **PostgreSQL and Flyway:** persist service data and queue/result records.
- **GitHub Actions:** runs backend verification for the Monitor Service and Monitoring Worker.

These components have been implemented incrementally. Do not infer that future workflows are already implemented because a component exists.

## 3. Planned Service Responsibilities

1. **Monitor Service:** monitor configuration, validation, lifecycle, and scheduling metadata.
2. **Monitoring Worker:** claim and execute due jobs. Health checks and keep-alive requests must be distinguishable by job purpose and result type.
3. **Auth Service:** identity, authentication, password hashing, and authorization.
4. **History Service:** paginated check and keep-alive execution history, response times, and documented uptime statistics.
5. **Incident Service:** confirmed outages and recovery.
6. **Notification Service:** outage/recovery alerts with bounded retries and deduplication.
7. **API Gateway:** frontend entry point when useful.
8. **React frontend:** monitors, keep-alive controls, history, incidents, and notification settings.

Implement these services only when their milestone is ready; avoid placeholder services without an end-to-end use case.

## 4. Keep-Alive Workflow (Planned)

1. A user configures a backend URL they are authorized to operate and selects a permitted interval.
2. The system validates the configuration and stores enabled/paused state and next execution time.
3. The scheduler makes due work available in the PostgreSQL-backed job queue.
4. The worker claims a job safely and executes a bounded HTTP/HTTPS request.
5. The system records execution time, status/outcome, response time, and failure category.
6. The user can pause or resume the schedule and inspect its history.
7. Duplicate processing and restart recovery are handled deliberately and covered by tests.

The exact API, database ownership, queue contract, and result persistence must be designed and reviewed before implementation.

## 5. Reliability Principles

- Keep network calls out of scheduler threads.
- Use bounded retries/backoff and timeouts.
- Make processing idempotent and prevent duplicate jobs/results.
- Handle unreachable targets, timeouts, worker restart, lease expiry, and scheduler failure.
- Use correlation IDs where useful.
- Do not claim production-level reliability until tested.

## 6. Data Ownership

Each service owns its tables and schema. No service may directly modify another service's tables. Use versioned REST APIs or documented event contracts. Avoid circular dependencies and unnecessary distributed transactions.

## 7. Security

Every user-provided health-check and keep-alive URL is an SSRF risk. Before public deployment:
- Allow only HTTP/HTTPS.
- Block loopback, private, link-local, reserved, and cloud-metadata destinations.
- Validate DNS resolutions and defend against DNS rebinding.
- Disable redirects initially or validate each destination.
- Apply connection and total timeouts, response-size and concurrency limits.
- Require authentication, authorization, ownership validation, rate limits, and narrow CORS.
- Do not expose internal endpoints, credentials, or stack traces.

Keep-alive schedules must respect provider rules and quotas; do not implement restriction-evasion behavior.

## 8. Zero-Cost and Operational Limits

Local development is the primary reliable environment. Use free/open-source tools and do not introduce paid infrastructure without explicit approval. A hosted scheduler cannot run while WatchHarbor itself is asleep or offline; document any free-tier sleep, quota, persistence, and availability limitations.

## 9. Status Discipline

Planned workflows are not implemented features. Update this document only when code, tests, and verification evidence support the status change.

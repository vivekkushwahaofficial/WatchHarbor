# Security Policy

## Reporting a Vulnerability

Report vulnerabilities privately to the repository maintainer rather than publishing exploit details in a public issue. Until a dedicated private channel is configured, use GitHub's private vulnerability reporting feature if enabled. Do not include passwords, access tokens, personal data, or production secrets.

Include the affected component/commit, impact, safe reproduction steps, and a suggested mitigation where possible. Allow time for investigation and a fix before public disclosure.

## Security Priorities

WatchHarbor is under development. Before public deployment, security work must include:
- Authentication, authorization, resource ownership checks, rate limiting, and narrow CORS.
- Strict validation for user-provided HTTP/HTTPS monitoring and keep-alive targets.
- Blocking loopback, private, link-local, reserved, and cloud-metadata destinations.
- DNS resolution validation, DNS rebinding defenses, and safe redirect handling.
- Connection/response timeouts, response-size limits, and bounded concurrency.
- Restricted internal service access, sanitized errors, and safe secret management.
- Keep-Alive Assistance limited to user-authorized endpoints and provider-permitted traffic. It must not try to bypass provider rules or quotas.

## Development Status

Some destination-validation and worker protections have been implemented and tested, but this does not mean every security requirement has been completed or independently audited. Do not treat WatchHarbor as production-ready until relevant controls are implemented and verified.

## Supported Versions

No formal release versions have been declared. Security support expectations will be documented when releases are established.

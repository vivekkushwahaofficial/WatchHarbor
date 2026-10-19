# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in WatchHarbor, please report it privately to the repository maintainer rather than publishing exploit details in a public issue.

Until a dedicated private reporting channel is configured, use GitHub's private vulnerability reporting feature if it is enabled for this repository. Do not include passwords, access tokens, personal data, or production secrets in a report.

Include the following information where possible:

- Affected component and version or commit.
- A description of the security issue and its potential impact.
- Reproduction steps that do not harm systems or access data without authorization.
- Suggested mitigation, if known.

Please allow time for investigation and a fix before publicly disclosing an unpatched vulnerability.

## Security Priorities

WatchHarbor's security requirements include:

- Authentication and authorization before public deployment.
- Resource ownership checks and appropriate rate limiting.
- Strict validation of monitored HTTP/HTTPS destinations.
- Blocking loopback, private, link-local, reserved, and cloud-metadata destinations.
- Validation of DNS resolutions and redirect destinations to mitigate SSRF and DNS rebinding.
- Connection and response timeouts, response-size limits, and bounded concurrency.
- Restricted internal service access and narrowly configured CORS.
- Safe secret management and sanitized error responses.

## Development Status

WatchHarbor is under active development. The presence of security-related validation and tests does not mean every security requirement has been implemented or independently audited.

Do not use an unfinished or unreviewed deployment to monitor sensitive systems or treat it as a production-ready security service.

## Supported Versions

No formal release versions have been declared yet. Security fixes and support expectations will be documented when release versions are established.

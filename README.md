# XporY Control Plane

Control-plane service built with Grails 3.3 that anchors policy authority, identity, and telemetry ingestion for whitelabel (WL) partners.

## Capabilities delivered

- **Whitelabel registry** – `POST /wls` to onboard WLs with baseline import/export/visibility policy defaults.
- **Policy retrieval** – `GET /wls/{id}/policies` returns the persisted baseline configuration with p95 latency instrumentation.
- **Token issuance** – `POST /wls/{id}/token` produces 5-minute JWTs with WL-scoped claims for downstream use.
- **Telemetry intake** – `POST /telemetry/events` accepts WL node events and appends them to the Postgres-backed `cp_telemetry` log.
- **Security posture** – mTLS-ready server configuration, JWT key rotation hooks, request correlation IDs, and OpenTelemetry spans for core flows.

## Getting started

### Prerequisites

- Java 8 (comes with Grails wrapper).
- Docker (optional, for container-based workflow).
- Postgres 14+ reachable through the credentials exported as environment variables.

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=xpory_cp
export DB_USERNAME=xpory_cp
export DB_PASSWORD=supersecret
export JWT_SIGNING_KEY=$(openssl rand -base64 32)
```

### Local run

```bash
./grailsw run-app
```

The server listens on `:8080` (configurable via `SERVER_PORT`). If TLS is enabled (see below) the HTTP endpoint is upgraded to HTTPS and requires client certificates.

### Docker build & run

```bash
./gradlew assemble
DOCKER_BUILDKIT=1 docker build -t xpory/cpservice:wave0 .
docker-compose up
```

This will start both the control-plane service and a Postgres instance with persistence under `./.data/postgres`.

## API reference

### `POST /wls`
Registers a new WL and seeds the default policy.

```json
{
  "name": "Acme WL",
  "description": "Pilot tenant",
  "contactEmail": "ops@acme.tld",
  "policy": {
    "importEnabled": true,
    "exportEnabled": false,
    "exportDelaySeconds": 5,
    "visibilityEnabled": true
  }
}
```

Response `201 Created`:
```json
{
  "id": "c0a80123-...",
  "baselinePolicy": {
    "importEnabled": true,
    "exportEnabled": false,
    "exportDelaySeconds": 5,
    "visibilityEnabled": true
  }
}
```

### `GET /wls/{id}/policies`
Returns the baseline policy for the WL. Instrumented with `cp.policies.fetch.latency` timer (targets p95 < 150 ms).

### `POST /wls/{id}/token`
Issues a JWT with ≤5 minute TTL. Optional body:
```json
{
  "scopes": ["policies:read", "telemetry:write"]
}
```

Response `201 Created` with token and TTL (seconds).

### `POST /telemetry/events`
Accepts either a single event object or `{ "events": [...] }` batch.

```json
{
  "whiteLabelId": "c0a80123-...",
  "nodeId": "edge-01",
  "eventType": "heartbeat",
  "payload": {"status": "green"},
  "eventTimestamp": "2025-06-18T10:15:30Z"
}
```

Response `202 Accepted` summarises persisted event ids.

## Observability

- **Correlation Ids** – inbound `X-Correlation-Id` header is honoured; otherwise generated. Surfaces in logs and response header.
- **Tracing** – OpenTelemetry spans wrap every request, honouring inbound `traceparent` headers and returning updated trace context. Exporters (OTLP/Jaeger) can be plugged in via Spring configuration.
- **Metrics** – Micrometer instruments policy fetch & pull latency, drift counters, and imbalance signal throughput (`cp.*` metrics). Replace the default registry with Prometheus/Datadog to scrape them.
- **Logging** – All entries include `correlationId`; e-mail/phone patterns are redacted at the appender to avoid leaking PII downstream.
- **Caching** – responses from `/wls/{id}/policies` and `/policies/pull` advertise `Cache-Control` with `app.policy.cacheTtlSeconds` (env `POLICY_CACHE_TTL_SECONDS`, default 300s) so WLs can honor the 1–5 min cache requirement.

## Security

- **mTLS** – configure `SERVER_SSL_*` and `MTLS_*` variables as covered in `docs/certificate-management.md`. The embedded Tomcat instance enforces client certificate authentication when enabled.
- **JWTs** – per-WL RSA key pairs stored in `cp_white_label_keys`; rotate with `POST /wls/{id}/keys/rotate` (exposes new `kid`) and serve the public JWK set at `/.well-known/jwks.json`.

## Tests

```bash
./gradlew check
```

Integration specs cover WL registration and telemetry persistence.


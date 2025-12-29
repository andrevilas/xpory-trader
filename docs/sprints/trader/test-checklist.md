# Control Plane Phase – Validation Checklist

This guide walks through end-to-end validation of the security, API, and admin
UI features introduced in this phase.

## Prerequisites
- Docker and docker-compose
- Node/npm (for admin UI dev build if needed)
- OpenSSL tools (already packaged in `xpory-pki` workflow)
- Local TLS proxy (optional): see `docs/local-proxy.md`

Assume commands run from repo root.

## 1. PKI / mTLS Rotation
1. Initialize or refresh root CA:
   ```bash
   cd xpory-pki
   ./bin/ca-init.sh   # skip if certs/ca.crt.pem already exists
   ```
2. Run automated rotation to issue gateway/WL certs:
   ```bash
   SERVER_CERT_DAYS=90 CLIENT_CERT_DAYS=90 \
   WL_A_CERT_PASSWORD=changeit WL_B_CERT_PASSWORD=changeit \
   ./bin/rotate-certs.sh
   ```
3. Confirm outputs: `nginx/certs/server.crt`, `/server.key`, `tls/wl-a/*.p12`,
   `tls/wl-b/*.p12`, `nginx/certs/clients/ca.crt`.
4. Restart stack to load new certs:
   ```bash
   docker compose up -d gateway xpory-wl-a
   ```
5. Manual verification:
   ```bash
   curl --cert tls/wl-a/wl_a.p12:changeit --cert-type P12 -k \
     https://cp.localhost/healthz
   ```

## 2. Control Plane Services
1. Build and start core stack:
   ```bash
   docker compose up -d postgres controlplane
   ```
2. Verify Liquibase ran (logs show “Grails application running”).
3. Issue a WL:
   ```bash
   curl --cert tls/wl-a/wl_a.p12:changeit --cert-type P12 -k \
     -H 'Content-Type: application/json' \
     -d '{"name":"WL A","contactEmail":"ops@example.com"}' \
     https://cp.localhost/wls
   ```
4. Fetch WL list (note `X-Policy-Cache-Ttl` header):
   ```bash
   curl --cert tls/wl-a/wl_a.p12:changeit --cert-type P12 -k -i \
     https://cp.localhost/wls
   ```
5. Update policy and confirm drift counter increments (optional: check Micrometer dump if using instrumentation).
6. Rotate WL key and fetch JWKS:
   ```bash
   curl --cert tls/wl-a/wl_a.p12:changeit --cert-type P12 -k \
     -X POST https://cp.localhost/wls/<WL_ID>/keys/rotate
   curl --cert tls/wl-a/wl_a.p12:changeit --cert-type P12 -k \
     https://cp.localhost/.well-known/jwks.json
   ```
7. Relationships:
   ```bash
   curl --cert tls/wl-a/wl_a.p12:changeit --cert-type P12 -k \
     -X PUT -H 'Content-Type: application/json' \
     -d '{"fxRate":1.5,"limitAmount":50000,"status":"active"}' \
     https://cp.localhost/relationships/<WL_A>/<WL_B>
   ```
8. Imbalance signals + ack:
   ```bash
   SIGNAL_ID=$(curl --cert tls/wl-a/wl_a.p12:changeit --cert-type P12 -k \
     -X POST -H 'Content-Type: application/json' \
     -d '{"sourceId":"<WL_A>","targetId":"<WL_B>","action":"block"}' \
     https://cp.localhost/imbalance/signals | jq -r '.id')
   curl --cert tls/wl-a/wl_a.p12:changeit --cert-type P12 -k \
     -X POST -H 'Content-Type: application/json' \
     -d '{"acknowledgedBy":"wl-agent"}' \
     https://cp.localhost/imbalance/signals/$SIGNAL_ID/ack
   ```
   Dispatch verification (CP → WL, HTTPS + JWT, no mTLS). For HTTP-only WLs,
   ensure `gatewayUrl` uses `http://` and confirm dispatch still succeeds.
   ```bash
   # Ensure target WL was registered with gatewayUrl set (POST /wls) or updated via PUT /wls/{id}
   # Re-send the signal and confirm dispatchStatus == sent/acknowledged in response
   ```
9. Trade-balance report:
   ```bash
   curl --cert tls/wl-a/wl_a.p12:changeit --cert-type P12 -k \
     https://cp.localhost/reports/trade-balance
   ```
10. JWKS / Token verification: issue token via `/wls/{id}/token`, decode with JWKS public key (using `jwt` CLI or `openssl`) ensuring `aud`, `wlId`, `kid` present.

## 3. Admin UI
1. Build UI bundle:
   ```bash
   cd admin-ui
   npm install
   npm run build
   cd ..
   ```
2. Start admin UI with compose:
   ```bash
   docker compose up -d admin-ui
   ```
3. Access via browser: `https://cp.localhost/admin/` (ensure browser trusts gateway cert or disable verification). Use WL client cert for mTLS.
4. Validate tabs:
   - White Labels: list & detail, policy form, “Rotate Signing Key” button (check message & JWKS).
   - Relationships: load & update using WL IDs; confirm snapshot matches API response.
   - Signals: emit block, acknowledge, view last signal details.
   - Reports: table matches `GET /reports/trade-balance` output.
   - Observability: JWKS table populated, metric/tracing info visible.

## 4. Gateway Protections
1. Rate limiting: issue rapid requests with client cert to `/healthz` and confirm `429` after burst.
   ```bash
   for i in {1..40}; do curl --cert tls/wl-a/wl_a.p12:changeit --cert-type P12 -k -o /dev/null -s https://cp.localhost/healthz; done
   ```
2. WAF check: request with suspicious string (e.g., `/wls?foo=../etc/passwd`) should return `403`.

## 5. Observability Spot Checks
1. Logs: `docker compose logs controlplane` should include `[cid:...]` and redact emails/phones (`[EMAIL_REDACTED]`).
2. Metrics: If Micrometer registry is exported (e.g., via Prometheus), ensure `cp.policies.fetch.latency` exposes `0.95` percentile; `cp.policies.drift` increments after policy change.
3. Tracing: Include a `Traceparent` header in curl and confirm response includes updated `traceparent`; check logs for correlation ID.

## 6. Tests & Postman
- Run unit/integration tests: `./gradlew check`.
- Import `docs/postman/control-plane.postman_collection.json` and exercise each request with client cert authentication.

## 7. Cleanup
```bash
docker compose down
docker volume prune
```

Ensure private keys and PKCS#12 bundles remain outside version control.

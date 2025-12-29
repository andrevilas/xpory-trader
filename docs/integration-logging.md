# CP ↔ WL Integration Logging

This guide describes where integration logs live for both apps and how to trace
an end-to-end CP → WL imbalance dispatch using correlation IDs.

## 1) Control Plane (xpory-trader)

**Logger category:** `cpservice.integration`

**What it logs:**
- signal recorded (`/imbalance/signals`)
- dispatch attempts to the WL gateway
- final result (acknowledged/failed)

**Where to read:**
- Dev: console output (e.g., `./grailsw run-app`)
- Docker: `docker compose logs -f controlplane`

**Example filter:**
```bash
rg "CP->WL" -n <(docker compose logs -f controlplane)
```

## 2) White Label node (xpory-core)

**Logger category:** `xpory.core.controlplane`

**What it logs:**
- JWT validation results (accepted/rejected)
- imbalance signal receive/ignore/apply
- block/unblock applied counts

**Where to read:**
- Dev: console output (Grails run)
- File (default): `${APP_LOG_PATH:-/logs}/xpory.core/ControlPlane/controlplane.log`

**Example filter:**
```bash
rg "CP-IMBALANCE|CP-AUTH" -n /home/andre/Trabalho/XporY/logs/xpory.core/ControlPlane/controlplane.log
```

## 3) Correlation IDs

- The CP dispatch now sets `X-Correlation-Id` on the request to the WL.
- The WL interceptor stores this in MDC and includes it in log lines.

**Tip:** send your own correlation ID when calling the CP, so it propagates
all the way to WL logs.

```bash
curl -k \
  -H 'X-Correlation-Id: demo-123' \
  --cert traefik/certs/wl-client.p12:changeit --cert-type P12 \
  -H 'Content-Type: application/json' \
  -d '{"sourceId":"<WL_A>","targetId":"<WL_B>","action":"block"}' \
  https://cp.localhost/imbalance/signals
```

## 4) Recommended trace flow (CP → WL)

1. Submit the signal to CP with `X-Correlation-Id`.
2. Watch CP logs (`cpservice.integration`) for dispatch attempts.
3. Watch WL logs (`xpory.core.controlplane`) for JWT validation and apply.

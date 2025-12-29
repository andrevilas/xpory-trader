# Local TLS Proxy (Traefik)

This project can run a local TLS proxy to emulate production where TLS is
terminated at the gateway and the apps run on HTTP.

## 1) Requirements

- Docker + Docker Compose
- `openssl` and `keytool`

## 2) Generate certs + run proxy

```bash
./scripts/run-traefik.sh
```

This script calls `scripts/setup-traefik-certs.sh` to generate local TLS
artifacts under `traefik/certs/`. These files are **not** versioned; run the
script on each machine to recreate them.

Add hostnames to `/etc/hosts`:

```
127.0.0.1 cp.localhost cp-jwks.localhost wl-importer.localhost wl-exporter.localhost
```

## 3) Ports and routes

- `https://cp.localhost` → CP (HTTP upstream on `localhost:8080`, mTLS required)
- `https://cp-jwks.localhost` → CP JWKS endpoint (no mTLS)
- `https://wl-importer.localhost` → WL importer (HTTP upstream on `localhost:8081`)
- `https://wl-exporter.localhost` → WL exporter (HTTP upstream on `localhost:8090`)

The CP router requires mTLS (client cert). WL routers do **not** require mTLS.
For CP admin endpoints behind the proxy, enable header-based admin auth:

```
ADMIN_HEADER_AUTH_ENABLED=true
ADMIN_HEADER_SUBJECTS=proxy-mtls
```

## 4) WL → CP (mTLS)

Use the generated client cert:

- `traefik/certs/wl-client.p12` (password: `changeit`)
- `traefik/certs/ca-truststore.p12` (password: `changeit`)

Example WL envs:

```
APP_CP_ENABLED=true
APP_CP_BASE_URL=https://cp.localhost
APP_CP_JWKS_URL=https://cp-jwks.localhost/.well-known/jwks.json
APP_CP_MTLS_ENABLED=true
APP_CP_MTLS_KEY_STORE=/home/andre/Trabalho/XporY/repos/github/xpory-trader/traefik/certs/wl-client.p12
APP_CP_MTLS_KEY_STORE_PASSWORD=changeit
APP_CP_MTLS_KEY_STORE_TYPE=PKCS12
APP_CP_MTLS_TRUST_STORE=/home/andre/Trabalho/XporY/repos/github/xpory-trader/traefik/certs/ca-truststore.p12
APP_CP_MTLS_TRUST_STORE_PASSWORD=changeit
APP_CP_MTLS_TRUST_STORE_TYPE=PKCS12
```

## 5) CP → WL (HTTPS + JWT, no mTLS)

Set WL `gatewayUrl` to the HTTPS host:

```
gatewayUrl: https://wl-importer.localhost
```

The proxy forwards HTTPS to the WL app over HTTP.

## 6) Remove proxy

```bash
docker compose -f docker-compose.proxy.yml down
```

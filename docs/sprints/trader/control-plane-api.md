# Control Plane API Overview

The control plane exposes a lightweight REST surface area secured by the Nginx
mTLS gateway. The table below summarises the active endpoints.

| Endpoint | Method(s) | Description |
|----------|-----------|-------------|
| `/wls` | `GET`, `POST` | List registered white-label tenants or create a new entry. |
| `/wls/{id}` | `GET`, `PUT` | Fetch or update metadata for a specific white-label (ex: `gatewayUrl`). |
| `/wls/{id}/policies` | `GET`, `PUT` | Read or update the baseline policy package attached to the tenant. |
| `/wls/{id}/token` | `POST` | Mint a scoped JWT for the tenant. |
| `/wls/{id}/keys/rotate` | `POST` | Rotate the tenant-specific JWT signing key (new `kid`). |
| `/wls/{id}/trader` | `GET`, `POST` | Inspect or authorise the Trader Account metadata for a WL; propagated to nodes via policy packages. |
| `/relationships/{src}/{dst}` | `GET`, `PUT` | Inspect or upsert the bilateral configuration (FX, limits, status) between two tenants. |
| `/policies/pull` | `POST` | Bulk export baseline policy data for Policy Agents (supports filtering by ID and `since`). |
| `/imbalance/signals` | `POST` | Record block/unblock signals emitted by risk automation. |
| `/imbalance/signals/{id}/ack` | `POST` | Confirm asynchronous receipt of a signal by the WL, capturing ack metadata. |
| `/telemetry/events` | `GET`, `POST` | Query or ingest telemetry events. |
| `/reports/trade-balance` | `GET` | Return consolidated relationship metrics for reporting. |
| `/.well-known/jwks.json` | `GET` | Public JWK set used to validate CP-issued JWTs. |

See `docs/postman/control-plane.postman_collection.json` for sample
requests and payloads. All endpoints expect client certificates issued by the
local XPORY PKI when accessed through the gateway.

Admin endpoints (`/wls`, `/relationships`, `/imbalance`, `/wls/{id}/policies`,
`/wls/{id}/trader`, `/wls/{id}/keys/rotate`, `/reports/trade-balance`) also
enforce client-certificate authentication on the CP. Optional allowlists:

- `ADMIN_CERT_SUBJECTS` (comma-separated X.500 subject DNs)
- `ADMIN_CERT_FINGERPRINTS` (comma-separated SHA-256 fingerprints)

## mTLS direction (production)

In production with Nginx/NPM terminating TLS:

- **WL → CP** uses mTLS (client cert at the CP gateway).
- **CP → WL** uses HTTPS + JWT, without mTLS, unless the WL gateway is explicitly
  configured to require client certificates.

Ensure `gatewayUrl` points to the WL public HTTPS host and that
`/.well-known/jwks.json` remains accessible to WL nodes.

## CP → WL dispatch contract (imbalance signals)
Control Plane dispatches imbalance signals to the WL gateway:

- `POST /control-plane/imbalance/signals`
- `Authorization: Bearer <jwt>` (RS256, `aud` + `wlId` must match the target WL id)
- `Content-Type: application/json`

Payload fields:
- `sourceId` (string, required)
- `targetId` (string, required)
- `action` (string, required: `block` | `unblock`)
- `reason` (string, optional)
- `initiatedBy` (string, optional)
- `effectiveFrom` (string, optional, ISO-8601 with offset, prefer `Z`)
- `effectiveUntil` (string, optional, ISO-8601 with offset, prefer `Z`)

Example:
```json
{
  "sourceId": "wl-exporter-id",
  "targetId": "wl-importer-id",
  "action": "block",
  "reason": "imbalance threshold exceeded",
  "effectiveFrom": "2025-12-29T15:13:30Z"
}
```

Policy responses (`GET /wls/{id}/policies` and `POST /policies/pull`) now embed a
`traderAccount` object whenever the Control Plane has issued Trader metadata for
the tenant. WL Nodes should persist the Trader locally (idempotently) and reply
with `trader.account.confirmed` telemetry once the record is stored.

## JWT Scopes Contract

Tokens issued by `/wls/{id}/token` must include the scopes required by the WL
runtime. The canonical set for the trader sprint is:

- `policies:read` — fetch policies via `/wls/{id}/policies` or `/policies/pull`.
- `telemetry:write` — emit telemetry to `/telemetry/events`.
- `offers:sync` — import sync job access.
- `trader:purchase` — authorize `/trader/purchase` on exporter WLs.

Tokens are RS256 and validated via `/.well-known/jwks.json` (`kid` required).

## Telemetry events

Canonical schema for cross-WL trade telemetry is defined in
`docs/sprints/trader/trader-purchase-telemetry.md`.

Trade-balance visibility depends on the telemetry payload content. The Control
Plane can only compute bilateral balances when events include both WL parties
(`originWhiteLabelId` + `targetWhiteLabelId`) and trade values (ex:
`confirmedQuantity`/`requestedQuantity` with `unitPrice`). If those fields are
missing from the event payloads, the CP will still store the telemetry but
cannot derive a bilateral balance between two WLs.

## WhiteLabel fields

The WL registry accepts a stable `id` (string) used by WL nodes to authenticate
and interact with the Control Plane. The `gatewayUrl` (base URL for CP → WL
dispatches such as imbalance signals) remains optional.

```json
{
  "id": "wl-a",
  "gatewayUrl": "https://wl-a.xpory.app"
}
```

## Policy Package Schema

The policy payload returned by `/wls/{id}/policies` and `/policies/pull` now
includes the following governance controls:

- `import_enabled` (`boolean`): whether the WL may ingest offers from other WLs.
- `export_enabled` (`boolean`): whether export is globally allowed.
- `export_delay_days` (`integer`): minimum number of days before a newly created
  offer may be exported.
- `visibility_wls` (`array<string>`): WL identifiers permitted to discover this
  WL’s exported offers.
- `traderAccount` (`object`): Trader metadata synchronised during Wave 0.
- `signature` (`object`): `{ value, algorithm, issuedAt }` HMAC over the full
  response payload, signed with `APP_CP_GOVERNANCE_SIGNING_SECRET`.
- `updatedBy` / `updatedSource`: audit fields for the last policy update.

Existing camelCase fields remain for backwards compatibility, but WL nodes
should prefer the snake_case variants.

## Relationship Package Schema

The relationship payload returned by `/relationships/{src}/{dst}` now exposes:

- `source_wl_id`, `dst_wl_id`: identifiers of the bilateral pair.
- `fx_rate` (`decimal`): cross-currency conversion factor.
- `imbalance_limit` (`decimal`): risk ceiling (used by later waves).
- `status` (`string`): `active`, `paused`, or `blocked`.
- `signature` (`object`): HMAC signature over the response body.
- `updatedBy` / `updatedSource`: audit fields for the last relationship update.

Every successful policy or relationship pull is logged in telemetry as
`POLICY_PACKAGE_SENT` or `RELATIONSHIP_PACKAGE_SENT` respectively. WL nodes
should verify signatures using the shared secret and cache the payload locally.

## Trade balance report

`/reports/trade-balance` now enriches relationships with `tradeMetrics`
aggregated from `TRADER_PURCHASE` telemetry and includes
`totals.tradeStatusTotals` (CONFIRMED/PENDING/REJECTED/UNKNOWN).

The report relies on `originWhiteLabelId`, `targetWhiteLabelId`, and value
fields from the telemetry payload. If WL nodes emit partial payloads, the
aggregate may be incomplete or zero for those WL pairs.

**Secrets**

- Control Plane signs packages with `APP_CP_GOVERNANCE_SIGNING_SECRET`.
- WL nodes must configure the same secret via `APP_CP_GOVERNANCE_SIGNING_SECRET`
  to validate signatures.

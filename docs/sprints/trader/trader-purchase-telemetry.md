# Trader Purchase Telemetry (CP)

This document defines the canonical telemetry event emitted by WL nodes for
cross-WL trades and consumed by the Control Plane.

## Event envelope

```json
{
  "whiteLabelId": "wl-importer",
  "nodeId": "wl-importer",
  "eventType": "TRADER_PURCHASE",
  "eventTimestamp": "2025-12-27T13:00:00Z",
  "payload": { "...": "..." }
}
```

## Payload schema

```json
{
  "role": "importer|exporter",
  "status": "PENDING|CONFIRMED|REJECTED",
  "tradeId": "uuid",
  "externalTradeId": "uuid",
  "originWhiteLabelId": "wl-exporter",
  "targetWhiteLabelId": "wl-importer",
  "originOfferId": "123",
  "requestedQuantity": 2,
  "confirmedQuantity": 2,
  "unitPrice": 120.00,
  "failureReason": "OUT_OF_STOCK",
  "idempotencyKey": "key",
  "executedAt": 1735304400000,
  "expiresAt": 1735390800000,
  "correlationId": "uuid",
  "source": "checkout|approval"
}
```

Notes:
- `originWhiteLabelId` and `targetWhiteLabelId` identify the WL pair used by
  `/reports/trade-balance` aggregation.
- `executedAt` and `expiresAt` are epoch millis.
- `confirmedQuantity` is optional when status is `PENDING`/`REJECTED`.

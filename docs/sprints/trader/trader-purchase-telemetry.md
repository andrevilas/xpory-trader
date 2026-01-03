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
  "role": "IMPORTER|EXPORTER",
  "status": "PENDING|CONFIRMED|REJECTED",
  "eventName": "TRADE_PENDING|TRADE_CONFIRMED|TRADE_REJECTED|TRADE_TIMEOUT|TRADE_ESCROWED|TRADE_SETTLED|TRADE_REFUNDED",
  "settlementStatus": "UNSETTLED|ESCROWED|SETTLED|REFUNDED",
  "tradeId": "uuid",
  "externalTradeId": "uuid",
  "originWhiteLabelId": "wl-exporter",
  "targetWhiteLabelId": "wl-importer",
  "originOfferId": "123",
  "requestedQuantity": 2,
  "confirmedQuantity": 2,
  "unitPrice": 120.00,
  "totalPrice": 240.00,
  "currency": "X",
  "failureReason": "OUT_OF_STOCK",
  "idempotencyKey": "key",
  "executedAt": 1735304400000,
  "expiresAt": 1735390800000,
  "correlationId": "uuid",
  "source": "checkout|approval|callback",
  "escrowXTransactionId": 123,
  "escrowReversalXTransactionId": 456,
  "settlementDebitXTransactionId": 789,
  "settlementCreditXTransactionId": 101,
  "settlementAt": 1735304400000
}
```

Notes:
- `originWhiteLabelId` and `targetWhiteLabelId` identify the WL pair used by
  `/reports/trade-balance` aggregation.
- `executedAt` and `expiresAt` are epoch millis.
- `confirmedQuantity` is optional when status is `PENDING`/`REJECTED`.
- `totalPrice` and `currency` represent the settled amount in X when
  available.
- `eventName` models the lifecycle transitions and is used to compute settled
  totals in `/reports/trade-balance`.
- `settlementStatus` captures the latest settlement outcome for the trade.
- Timeouts are emitted as `eventName=TRADE_TIMEOUT`, while `status` remains
  `REJECTED` with `failureReason=PENDING_TIMEOUT`.

## Trade balance derivation

The Control Plane can only derive bilateral trade balances when the payload
includes both WL identifiers and value fields. Minimum required fields for
aggregation:

- `originWhiteLabelId`
- `targetWhiteLabelId`
- `unitPrice`
- `requestedQuantity` or `confirmedQuantity`
- `eventName` or `settlementStatus` (to identify settled trades)

If any of these are missing in emitted events, the telemetry can still be
stored, but the trade-balance report cannot compute accurate values for that
WL pair.

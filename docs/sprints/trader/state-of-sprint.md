# Estado atual — Sprint Trader (CP)

Data: 2026-03-05

## Resumo

- CP ingere telemetria `TRADER_PURCHASE` com idempotência e projeção de trades.
- `/reports/trade-balance` segue em `v1` por padrão (telemetria) e suporta `v2` por parâmetro (projeção + fallback).
- Admin endpoints protegidos por mTLS + allowlist de header/cert.
- Aprovacoes manuais de trades pendentes com login JWT e usuarios MASTER/TRADER.
- Reconciliacao periódica `cp_trades` vs telemetria com alerta de divergência para MASTER/MANAGER.

## Estado CP (xpory-trader)

- `/telemetry/events` armazena eventos de trade cross-WL com `idempotencyKey` e `dedupeFingerprint`.
- `cp_trades` mantém projeção consolidada por `externalTradeId`.
- `/reports/trade-balance` agrega por par WL e status (CONFIRMED/PENDING/REJECTED).
- `eventName=TRADE_SETTLED` ou `settlementStatus=SETTLED` alimentam totais settled.
- Filtros suportados: `from`, `to`, `wlId`, `wlImporter`, `wlExporter`.
- Versão do relatório: `v1` (default) e `v2` por `version=v2|useProjection=true|reportVersion=v2|mode=projection`.
- `/trades/pending` lista pendencias com base na telemetria (`TRADER_PURCHASE` PENDING).
- `/trades/{tradeId}/approve|reject` persiste decisao e aciona WL exportadora.
- `/auth/login` e `/users` gerenciam login e cadastro interno (MASTER/TRADER).

## Dependencias do WL (xpory-core)

- WL emite `eventName` padronizado:
  `TRADE_PENDING`, `TRADE_CONFIRMED`, `TRADE_REJECTED`, `TRADE_TIMEOUT`,
  `TRADE_ESCROWED`, `TRADE_SETTLED`, `TRADE_REFUNDED`.
- `settlementStatus`: `UNSETTLED`, `ESCROWED`, `SETTLED`, `REFUNDED`.
- `currency`: `X`.

## Pendencias relevantes

- Dashboard/admin UI para explorar divergencias de reconciliacao com drill-down por trade.
- End-to-end de operacao de reconciliacao em ambiente staging com carga real.

## Links principais (xpory-trader)

- Workplan CP: `docs/sprints/trader/workplan.md`
- API CP: `docs/sprints/trader/control-plane-api.md`
- Telemetria CP: `docs/sprints/trader/trader-purchase-telemetry.md`
- Runbook Admin UI: `docs/runbooks/admin-ui.md`
- Workplan Aprovacoes: `docs/sprints/trader/manual-approvals-workplan.md`

## Referencias cruzadas (xpory-core)

- Workplan WL: `/home/andre/Trabalho/XporY/repos/xpory-core/docs/sprints/trader/transactions-workplan.md`
- Balanca comercial global: `/home/andre/Trabalho/XporY/repos/xpory-core/docs/sprints/trader/commercial-balance-workplan.md`
- Fluxo cross-WL: `/home/andre/Trabalho/XporY/repos/xpory-core/docs/sprints/trader/cross-wl-purchase.md`
- Contratos CP: `/home/andre/Trabalho/XporY/repos/xpory-core/docs/control-plane-contracts.md`

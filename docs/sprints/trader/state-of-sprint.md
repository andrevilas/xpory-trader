# Estado atual — Sprint Trader (CP)

Data: 2026-01-03

## Resumo

- CP ingere telemetria `TRADER_PURCHASE` e expõe `/reports/trade-balance` baseado nos eventos.
- Admin endpoints protegidos por mTLS + allowlist de header/cert.
- Balanca global consolidada permanece em planejamento no WL (xpory-core).

## Estado CP (xpory-trader)

- `/telemetry/events` armazena eventos de trade cross-WL.
- `/reports/trade-balance` agrega por par WL e status (CONFIRMED/PENDING/REJECTED).
- `eventName=TRADE_SETTLED` ou `settlementStatus=SETTLED` alimentam totais settled.
- Filtros suportados: `from`, `to`, `wlId`, `wlImporter`, `wlExporter`.

## Dependencias do WL (xpory-core)

- WL emite `eventName` padronizado:
  `TRADE_PENDING`, `TRADE_CONFIRMED`, `TRADE_REJECTED`, `TRADE_TIMEOUT`,
  `TRADE_ESCROWED`, `TRADE_SETTLED`, `TRADE_REFUNDED`.
- `settlementStatus`: `UNSETTLED`, `ESCROWED`, `SETTLED`, `REFUNDED`.
- `currency`: `X`.

## Pendencias relevantes

- Balanca comercial global (modelo central + reconciliacao) ainda em planejamento.

## Links principais (xpory-trader)

- Workplan CP: `docs/sprints/trader/workplan.md`
- API CP: `docs/sprints/trader/control-plane-api.md`
- Telemetria CP: `docs/sprints/trader/trader-purchase-telemetry.md`
- Runbook Admin UI: `docs/runbooks/admin-ui.md`

## Referencias cruzadas (xpory-core)

- Workplan WL: `/home/andre/Trabalho/XporY/repos/xpory-core/docs/sprints/trader/transactions-workplan.md`
- Balanca comercial global: `/home/andre/Trabalho/XporY/repos/xpory-core/docs/sprints/trader/commercial-balance-workplan.md`
- Fluxo cross-WL: `/home/andre/Trabalho/XporY/repos/xpory-core/docs/sprints/trader/cross-wl-purchase.md`
- Contratos CP: `/home/andre/Trabalho/XporY/repos/xpory-core/docs/control-plane-contracts.md`

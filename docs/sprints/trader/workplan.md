# Work Plan — Sprint Trader (Control Plane)

Última atualização: 2025-12-27

Este plano resume o estado atual do Control Plane (CP) e o roadmap de
alinhamento para cumprir o contrato documentado da sprint trader.

Legenda de status:
- [ ] Não iniciado
- [~] Em andamento
- [x] Concluído

---

## Estado atual (CP)

### Implementado
- Políticas base de WL (`/wls/{id}/policies`, `/policies/pull`) com assinatura HMAC e cache headers.
- Registro de WL (`POST /wls`) e emissão de token (`POST /wls/{id}/token`).
- Relacionamentos WL↔WL (`/relationships/{src}/{dst}`) com `fxRate`, `imbalanceLimit`, `status`.
- Telemetria (`/telemetry/events`) com persistência e confirmação de trader via evento `trader.account.confirmed`.
- Imbalance signals (registro + ack) (`POST /imbalance/signals`, `POST /imbalance/signals/{id}/ack`).
- JWKS e rotação de chave por WL (`/.well-known/jwks.json`, `/wls/{id}/keys/rotate`).
- Observabilidade básica (correlation id + OTel) e redaction de PII no log.

### Pontos de atenção
- JWT: decisão confirmada para RS256 + JWKS; documentação alinhada.
- Imbalance signals agora incluem dispatch CP → WL com status persistido.
- Telemetria de trade cross-WL agora alimenta relatórios/balanço.

---

## Roadmap de alinhamento

### Fase A — Contratos e segurança
- [x] Alinhar JWT com a documentação (decisão: manter RS256 + JWKS e atualizar docs).
- [x] Validar audience/claims do token para fluxo `/trader/purchase`:
  - garantir que o `aud`/`scopes` gerados pelo CP são compatíveis com o validador do WL.
- [x] Documentar formalmente o contrato de scopes esperados (ex.: `policies:read`, `trader:purchase`, `telemetry:write`).
- [x] Definir e aplicar autenticação/autorizações para endpoints administrativos do CP
  (`/wls`, `/relationships`, `/imbalance`, `/wls/{id}/policies`).

### Fase B — Imbalance signals (CP → WL)
- [x] Adicionar no cadastro de WL um campo de endpoint base (ex.: `gatewayUrl`) para dispatch.
- [x] Implementar dispatcher de sinais para WL (`/api/v2/control-plane/imbalance/signals`):
  - envio mTLS, retries com backoff, e storage de status (pendente/enviado/ack).
- [x] Implementar confirmação automática via `/imbalance/signals/{id}/ack` quando WL responde com sucesso.
- [ ] Testes de integração para dispatch/ack.

### Fase C — Telemetria e relatórios
- [x] Mapear eventos de trade cross-WL enviados pelo WL e definir schema oficial.
- [x] Enriquecer o relatório `/reports/trade-balance` usando telemetria/ledger real
  (fonte: eventos `TRADER_PURCHASE` emitidos pelo WL; hoje usa apenas limites de relacionamento).
- [x] Criar métricas para taxa de PENDING/REJECTED/CONFIRMED (CP-side).

### Fase D — Governança e auditoria
- [x] Registrar `updatedBy`/`source` em atualizações de políticas, relações e sinais.
- [x] Versionar mudanças de políticas (ex.: `policyRevision`) com histórico/auditoria.

---

## Status geral

### Progresso por fase
- Fase A: concluída.
- Fase B: concluída (pendente apenas testes de integração).
- Fase C: concluída.
- Fase D: concluída.

---

## Observações
- Este plano assume como fonte de verdade os contratos descritos na documentação atual da sprint trader e nos docs do CP.
- Divergências de documentação (ex.: JWT HS256 vs RS256) devem ser resolvidas antes de ajustes de implementação.
- O endpoint `/reports/trade-balance` atual e baseado em telemetria `TRADER_PURCHASE`; a balanca comercial global permanece em planejamento no workplan do xpory-core (`docs/sprints/trader/commercial-balance-workplan.md`).
- Estado atual consolidado: `docs/sprints/trader/state-of-sprint.md`.

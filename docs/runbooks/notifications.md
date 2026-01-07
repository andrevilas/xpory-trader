# Runbook: Notificacoes (CP + Admin UI)

## Objetivo
Validar notificacoes em tempo real (STOMP/SockJS) e APIs de listagem/leitura.

## Pre-requisitos
- CP (xpory-trader) rodando com migrations aplicadas.
- Admin UI rodando com `VITE_CP_BASE_URL` apontando para o CP.
- Token valido de um usuario admin (MASTER/MANAGER/TRADER conforme o teste).

## Passos

### 1) Endpoints HTTP
Base: `${CP_BASE_URL}` (ex.: http://localhost:8082/admin/api)

- Listar notificacoes:
  - `GET /notifications?limit=50&offset=0`
- Contador nao lidas:
  - `GET /notifications/unread-count`
- Marcar como lida:
  - `POST /notifications/{id}/read`

### 2) WebSocket (STOMP/SockJS)
Endpoint SockJS: `${CP_WS_URL}/wsxpory`

Topico por usuario:
- `/topic/{userId}`

Payload esperado:
```
{"kind":"notification","payload":{...}}
```

### 3) Fluxos de validacao
1) Login e token
- Autentique no CP e capture o bearer token.

2) Conexao WebSocket
- Conecte via STOMP/SockJS usando `Authorization: Bearer <token>`.
- Assine `/topic/{userId}`.

3) Telemetria e notificacoes
- Gere eventos `TRADER_PURCHASE` (CONFIRMED e PENDING) via telemetria.
- Verifique:
  - `TRADE_NEW` quando CONFIRMED.
  - `TRADE_PENDING` quando PENDING + role EXPORTER.
  - `LIMIT_WARNING` quando >= 70% do limite.
  - `LIMIT_REACHED` quando >= 100% do limite.

4) Regras de audiencia
- `TRADE_NEW` e `TRADE_PENDING`: todos os usuarios ativos.
- `LIMIT_WARNING` e `LIMIT_REACHED`: apenas MASTER e MANAGER.

5) Listagem e leitura
- `GET /notifications` retorna itens + count.
- `GET /notifications/unread-count` reflete o numero correto.
- `POST /notifications/{id}/read` marca como lida.

## Validacao
- Mensagens chegam no topico do usuario correto.
- Contador de nao lidas atualiza apos marcar como lida.
- Filtros de audiencia respeitam roles.

## Validado em 2026-01-07
- Lista de notificacoes carrega e contador de nao lidas atualiza.
- Clique na notificacao navega para o fluxo relacionado.

## Observacoes
- `LIMIT_*` considera soma de trades CONFIRMED + PENDING.
- Mensagem inclui `origin -> target | total / limite` quando aplicavel.
- Rotas em Admin UI:
  - `OPEN_TRADES` -> `/trades?sourceId=...&targetId=...`
  - `OPEN_PENDING_TRADES` -> `/trades/pending?wlExporter=...&wlImporter=...`
  - `OPEN_RELATIONSHIP` -> `/relationships?sourceId=...&targetId=...`

## Relacionados
- `admin-ui.md`
- `reset-environment.md`

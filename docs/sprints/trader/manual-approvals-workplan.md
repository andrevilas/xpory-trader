# Work Plan — Sprint Trader: Aprovação Manual de Solicitações (CP)

Ultima atualizacao: 2026-01-03

Objetivo: permitir que um usuario do Control Plane liste solicitacoes de comercio
cross-WL pendentes e aprove/rejeite quando o fluxo nao for automatico.

## Escopo
- Listar trades pendentes (PENDING) entre WLs.
- Permitir aprovar/rejeitar solicitacoes no exporter WL.
- Garantir auditoria (quem aprovou/rejeitou, quando, motivo).
- Exibir resultado no Admin UI (xpory-trader-admin-ui).
- Gestao de usuarios no CP com perfis MASTER e TRADER.

## Premissas
- O exporter WL e a fonte de verdade para aprovacao/rejeicao.
- O CP deve obter um peer-token valido para autenticar a chamada no exporter.
- Telemetria `TRADER_PURCHASE` e a unica fonte para listar pendencias no CP.
- O CP persiste decisoes (auditoria e estado), antes e depois de chamar o exporter.
- Apenas usuarios autenticados podem acessar a lista/acoes.
- Basic Auth e aplicado no proxy (Traefik em dev, Nginx em prod).
- Login do usuario gera JWT do CP (auth interna).
- Perfis:
  - MASTER: todas as permissoes administrativas.
  - TRADER: listar pendencias e aprovar/rejeitar.

## API proposta (CP)

### 1) Listar pendencias
`GET /trades/pending`

Filtros:
- `wlExporter` (opcional)
- `wlImporter` (opcional)
- `from` / `to` (ISO-8601, opcional)

Resposta:
```json
{
  "items": [
    {
      "tradeId": "uuid",
      "externalTradeId": "uuid",
      "originWhiteLabelId": "wl-exporter",
      "targetWhiteLabelId": "wl-importer",
      "originOfferId": "123",
      "requestedQuantity": 1,
      "unitPrice": 120.0,
      "status": "PENDING",
      "eventTimestamp": "2026-01-03T12:00:00Z",
      "source": "telemetry"
    }
  ],
  "count": 1
}
```

### 2) Aprovar pendencia
`POST /trades/{tradeId}/approve`

Body:
```json
{
  "wlExporter": "wl-exporter",
  "reason": "ADMIN_APPROVED"
}
```

### 3) Rejeitar pendencia
`POST /trades/{tradeId}/reject`

Body:
```json
{
  "wlExporter": "wl-exporter",
  "reason": "ADMIN_REJECTED"
}
```

Notas:
- `tradeId` vem diretamente do payload de telemetria (campo `tradeId`).
- `externalTradeId` e retornado apenas para referencia cruzada.

### 4) Login de usuario (auth interna)
`POST /auth/login`

Body:
```json
{
  "email": "user@xpory.com",
  "password": "secret"
}
```

### 5) CRUD de usuarios (somente MASTER)

#### Listar usuarios
`GET /users`

Resposta:
```json
{
  "items": [
    {
      "id": "uuid",
      "email": "user@xpory.com",
      "role": "TRADER",
      "status": "active",
      "lastLoginAt": "2026-01-03T12:00:00Z"
    }
  ],
  "count": 1
}
```

#### Criar usuario
`POST /users`

Body:
```json
{
  "email": "user@xpory.com",
  "password": "secret",
  "role": "TRADER"
}
```

#### Atualizar usuario
`PUT /users/{id}`

Body (parcial):
```json
{
  "role": "MASTER",
  "status": "disabled"
}
```

#### Reset de senha
`POST /users/{id}/reset-password`

Body:
```json
{
  "password": "new-secret"
}
```

Resposta:
```json
{
  "token": "jwt",
  "expiresInSeconds": 3600,
  "user": {
    "id": "uuid",
    "email": "user@xpory.com",
    "role": "MASTER"
  }
}
```

## UI (Admin UI)
- Nova tela: "Aprovacoes de trade".
- Filtros: wlExporter, wlImporter, periodo.
- Lista de pendencias com acao "Aprovar" e "Rejeitar" (com motivo opcional).
- Mostrar estado final e atualizar a lista apos acao.

## Auditoria e telemetria
- Registrar `updatedBy`, `updatedAt`, `decision` em tabela propria no CP.
- Emitir evento administrativo (ex: `TRADE_APPROVAL_DECISION`) como espelho da decisao.
- Registrar `decidedByUserId` e `decidedByRole` na persistencia.

## Modelo de dados (CP)

### cp_users
Campos sugeridos:
- `id` (uuid, PK)
- `email` (string, unique)
- `password_hash` (string)
- `role` (`MASTER` | `TRADER`)
- `status` (`active` | `disabled`)
- `last_login_at` (timestamp)
- `created_at`, `updated_at`

### cp_trade_approvals
Persistencia de decisoes administrativas.
Campos sugeridos:
- `id` (uuid, PK)
- `trade_id` (string, id do payload de telemetria)
- `external_trade_id` (string, opcional)
- `origin_white_label_id` (string)
- `target_white_label_id` (string)
- `decision` (`APPROVED` | `REJECTED`)
- `reason` (string, opcional)
- `requested_quantity` (integer, opcional)
- `unit_price` (decimal, opcional)
- `currency` (string, default `X`)
- `status_before` (string, default `PENDING`)
- `status_after` (string, preenchido apos resposta do exporter)
- `exporter_response_code` (integer, opcional)
- `exporter_response_body` (text, opcional)
- `decided_by_user_id` (uuid, FK -> cp_users)
- `decided_by_role` (`MASTER` | `TRADER`)
- `created_at`, `updated_at`

Indices recomendados:
- `trade_id` (unique)
- `origin_white_label_id`, `target_white_label_id`
- `created_at`

## Backlog por fase

### Fase A — Contrato e modelo
- [ ] Definir contrato de listagem e acao (aprovar/rejeitar).
- [ ] Definir modelo de auditoria (tabela ou evento).
- [ ] Definir modelo de usuario e permissao (MASTER/TRADER).
- [ ] Atualizar docs do CP e do UI.

### Fase B — Implementacao CP
- [ ] Endpoint `GET /trades/pending`.
- [ ] Integracao com telemetry store para listar pendencias.
- [ ] Implementar aprovacao/rejeicao chamando exporter WL
      (`/api/v2/trader/purchases/{id}/approve|reject`).
- [ ] Garantir peer-token com scope `trader:purchase`.
- [ ] CRUD basico de usuarios (MASTER cria/edita/desativa).
- [ ] AuthN/AuthZ: MASTER e TRADER com permissoes definidas.
- [ ] Endpoint `/auth/login` para emissao de JWT interno.
- [ ] Persistir decisoes em `cp_trade_approvals`.

### Fase C — UI
- [ ] Tela de listagem e filtros.
- [ ] Acoes de aprovar/rejeitar com feedback.
- [ ] States de loading/erro.
- [ ] Tela de gestao de usuarios (somente MASTER).

### Fase D — Testes
- [ ] Unit tests dos endpoints CP.
- [ ] Teste de integracao (CP -> WL approve/reject).
- [ ] Teste UI (fluxo de aprovacao manual).

### Fase E — Runbook
- [ ] Runbook operacional para aprovacao manual.
- [ ] Exemplo de payload e passos para reproduzir.

## Riscos e perguntas abertas
- Como evitar aprovar trade ja resolvido no WL?
- Como evitar duplicidade de aprovacao/rejeicao no CP (idempotencia)?
- Como garantir expiração/rotacao de credenciais do Basic Auth no proxy?

## Senhas e seguranca

- Hash recomendado: BCrypt (cost >= 12).
- Nunca retornar `password_hash` nas respostas.
- Reset de senha apenas por usuario MASTER autenticado.
- Token JWT com exp curto (ex: 1h) e refresh via login.

## Permissoes por endpoint

### MASTER
- Acesso total a endpoints admin (`/wls`, `/relationships`, `/imbalance`, `/reports/*`).
- CRUD de usuarios (`/users`).
- Listar e decidir pendencias (`/trades/pending`, `/trades/{id}/approve|reject`).

### TRADER
- Listar pendencias (`/trades/pending`).
- Aprovar/rejeitar pendencias (`/trades/{id}/approve|reject`).

### Publico (com Basic Auth)
- `POST /auth/login` (protegido por Basic Auth no proxy).

## Sequencia de autenticacao (fluxo final)

1) Proxy (Traefik/Nginx) aplica Basic Auth.
2) Usuario realiza login em `/auth/login`.
3) CP emite JWT interno com `role` e `userId`.
4) Admin UI envia `Authorization: Bearer <jwt>` nas chamadas.
5) CP valida o JWT e aplica autorizacao por role.

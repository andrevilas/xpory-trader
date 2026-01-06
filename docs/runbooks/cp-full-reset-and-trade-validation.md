# Runbook: Reset completo CP + WLs e validacao de trades

Data base: 2026-01-06 (BRT)

## Objetivo
Resetar o ambiente local (CP + WLs), recriar WLs/politicas/relacionamentos via API, sincronizar ofertas e gerar ~20 trades com statuses mistos (PENDING/CONFIRMED/REJECTED), validando detalhes via Admin UI/Playwright.

## Pre-requisitos
- CP + Traefik ativos (xpory-trader).
- Exporter (wl-exporter) e Importer (wl-importer) ativos (xpory-core).
- /etc/hosts:
  - 127.0.0.1 cp.localhost cp-jwks.localhost wl-importer.localhost wl-exporter.localhost
- Certificados Traefik do repo xpory-trader.

## 0) Subir stack
```bash
# CP + Postgres + Admin UI
cd /home/andre/Trabalho/XporY/repos/github/xpory-trader

docker compose up -d

# Traefik (mTLS)
docker compose -f docker-compose.proxy.yml up -d
```

## 1) Limpeza CP (apenas dados)
```bash
docker exec -i xpory-cp-postgres psql -U xpory_cp -d xpory_cp \
  -c "TRUNCATE TABLE cp_trade_approvals, cp_telemetry, cp_relationships, cp_white_label_policy_revisions, cp_white_label_policies, cp_white_label_keys, cp_trader_accounts, cp_white_labels, cp_users, cp_imbalance_signals RESTART IDENTITY CASCADE;"

# restart para bootstrap de usuarios
docker compose restart controlplane
```

## 2) Limpeza WLs (somente caches e trades/importacao)
```bash
# Exporter (wl-01) - limpa caches e trades
 docker exec -i postgres-db psql -U postgres -d "wl-01" \
  -c "TRUNCATE TABLE cp_policy_cache, cp_relationship_cache, offer_export_index, cross_wl_trade RESTART IDENTITY CASCADE;"

# Importer (wl-03) - limpa caches e importacao
 docker exec -i postgres-db psql -U postgres -d "wl-03" \
  -c "TRUNCATE TABLE cp_policy_cache, cp_relationship_cache, offer_export_index, imported_offer, cross_wl_trade RESTART IDENTITY CASCADE;"
```

## 3) Recriar WLs no CP (API)
```bash
TOKEN=$(curl -sk -X POST https://cp.localhost/admin/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"master@xpory.local","password":"changeit"}' | python3 -c 'import json,sys;print(json.load(sys.stdin)["token"])')

# WL exporter
curl -sk -X POST https://cp.localhost/admin/api/wls \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"id":"bf695d35-6b28-42b6-8bc7-772385a05e5a","name":"wl-exporter","description":"Exporter WL","contactEmail":"exporter@example.com","gatewayUrl":"http://host.docker.internal:8081","status":"active","policy":{"importEnabled":false,"exportEnabled":true,"exportDelaySeconds":0,"visibilityEnabled":true,"policyRevision":"baseline"}}'

# WL importer
curl -sk -X POST https://cp.localhost/admin/api/wls \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"id":"d649e009-da37-4593-84b2-3cd50800f325","name":"wl-importer","description":"Importer WL","contactEmail":"importer@example.com","gatewayUrl":"http://host.docker.internal:8090","status":"active","policy":{"importEnabled":true,"exportEnabled":false,"exportDelaySeconds":0,"visibilityEnabled":true,"policyRevision":"baseline"}}'

# Politicas (visibilidade mutua)
curl -sk -X PUT https://cp.localhost/admin/api/wls/bf695d35-6b28-42b6-8bc7-772385a05e5a/policies \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"importEnabled":false,"exportEnabled":true,"exportDelaySeconds":0,"visibilityEnabled":true,"visibilityWls":["d649e009-da37-4593-84b2-3cd50800f325"],"policyRevision":"baseline","updatedBy":"bootstrap","updatedSource":"runbook"}'

curl -sk -X PUT https://cp.localhost/admin/api/wls/d649e009-da37-4593-84b2-3cd50800f325/policies \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"importEnabled":true,"exportEnabled":false,"exportDelaySeconds":0,"visibilityEnabled":true,"visibilityWls":["bf695d35-6b28-42b6-8bc7-772385a05e5a"],"policyRevision":"baseline","updatedBy":"bootstrap","updatedSource":"runbook"}'

# Relacionamentos (bidirecionais)
for SRC in bf695d35-6b28-42b6-8bc7-772385a05e5a d649e009-da37-4593-84b2-3cd50800f325; do
  if [ "$SRC" = "bf695d35-6b28-42b6-8bc7-772385a05e5a" ]; then DST=d649e009-da37-4593-84b2-3cd50800f325; else DST=bf695d35-6b28-42b6-8bc7-772385a05e5a; fi
  curl -sk -X PUT "https://cp.localhost/admin/api/relationships/${SRC}/${DST}" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -d '{"status":"active","fxRate":1,"limitAmount":100000,"updatedBy":"bootstrap","updatedSource":"runbook"}'
 done

# Trader accounts
curl -sk -X POST https://cp.localhost/admin/api/wls/bf695d35-6b28-42b6-8bc7-772385a05e5a/trader \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Conta Trader Exporter","status":"active","contactEmail":"exporter@example.com","contactPhone":"+55 11 90000-0001"}'

curl -sk -X POST https://cp.localhost/admin/api/wls/d649e009-da37-4593-84b2-3cd50800f325/trader \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Conta Trader Importer","status":"active","contactEmail":"importer@example.com","contactPhone":"+55 11 90000-0002"}'
```

## 4) Gerar peer token (mTLS) e sincronizar ofertas
```bash
curl -s -o /tmp/peer-token.json -w "%{http_code}" \
  --resolve cp.localhost:443:127.0.0.1 \
  --cacert /home/andre/Trabalho/XporY/repos/github/xpory-trader/traefik/certs/ca.crt \
  --cert /home/andre/Trabalho/XporY/repos/github/xpory-trader/traefik/certs/wl-client.p12:changeit \
  --cert-type P12 \
  -H 'Content-Type: application/json' \
  -d '{"targetWlId":"bf695d35-6b28-42b6-8bc7-772385a05e5a","scopes":["offers:sync","trader:purchase"]}' \
  https://cp.localhost/wls/d649e009-da37-4593-84b2-3cd50800f325/peer-token

TOKEN=$(python3 - <<'PY'
import json
with open('/tmp/peer-token.json') as f:
    print(json.load(f).get('token',''))
PY
)

curl -s -o /tmp/import-sync.json -w "%{http_code}" \
  -X POST http://localhost:8090/api/v2/control-plane/debug/import \
  -H "Authorization: Bearer $TOKEN"
```

## 5) Gerar trades via API (buy-offer)
- Use uma conta ativa no wl-03 com `buy_offer=true` e token valido.
- Exemplo de conta ja ativa (ajuste se necessario):
  - account_id=8780, token=codex-token-3

```bash
# pegar 20 offers importadas elegiveis
OFFER_IDS=$(docker exec -i postgres-db psql -U postgres -d "wl-03" -t -A \
  -c "SELECT offer_id FROM imported_offer WHERE eligible=true AND lifecycle_status='ENABLED' AND COALESCE(offers_available,0) > 0 LIMIT 20;")

for OFFER_ID in $OFFER_IDS; do
  curl -s -X POST http://localhost:8090/api/v1/buy-offer \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode "accountId=8780" \
    --data-urlencode "token=codex-token-3" \
    --data-urlencode "offerId=${OFFER_ID}" \
    --data-urlencode "quantity=1" \
    --data-urlencode 'paymentParams={"paymentType":"balanceAccount"}'
  echo
 done > /tmp/buy-offer-results.jsonl
```

## 6) Aprovar/rejeitar trades no exporter
```bash
TOKEN=$(python3 - <<'PY'
import json
with open('/tmp/peer-token.json') as f:
    print(json.load(f).get('token',''))
PY
)

# listar pendentes no exporter
curl -s http://localhost:8081/api/v2/trader/purchases/pending \
  -H "Authorization: Bearer $TOKEN" > /tmp/pending-trades.json

# aprovar/rejeitar ids do arquivo (exemplo manual)
# APPROVE
curl -s -X POST http://localhost:8081/api/v2/trader/purchases/<TRADE_ID>/approve \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}'

# REJECT
curl -s -X POST http://localhost:8081/api/v2/trader/purchases/<TRADE_ID>/reject \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"reason":"MANUAL_REJECT"}'
```

## 7) Validar CP detalhes
```bash
TOKEN=$(curl -sk -X POST https://cp.localhost/admin/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"master@xpory.local","password":"changeit"}' | python3 -c 'import json,sys;print(json.load(sys.stdin)["token"])')

curl -s https://cp.localhost/admin/api/trades \
  -H "Authorization: Bearer $TOKEN" | head -c 1000

# detalhes
curl -s https://cp.localhost/admin/api/trades/<TRADE_ID>/details \
  -H "Authorization: Bearer $TOKEN"
```

## 8) Validar preflight (hold curto)
Objetivo: garantir que o preflight bloqueia compras impossiveis **antes** do pagamento.

### 8.0) Usuario QA (wl-03)
- login: qa.preflight@xpory.local
- account_id: 90805
- entity_id: 90803
- token: qa-preflight-token
- saldo X$ e R$: 5000

### 8.1) Preflight rejeitado por PRICE_CHANGED
1. No exporter (wl-01), altere o preco da oferta originaria apos a sincronizacao.
2. No importer (wl-03), execute o `/api/v1/buy-offer` da oferta importada.
3. Esperado:
   - Resposta `ok=false`, `status=REJECTED`, `msg` indicando atualizacao de preco.
   - Nenhuma comissao cobrada (sem estorno necessario).

### 8.2) Preflight rejeitado por OUT_OF_STOCK
1. No exporter, zere o estoque da oferta originaria.
2. No importer, repita o `/api/v1/buy-offer`.
3. Esperado:
   - Resposta `ok=false`, `status=REJECTED`, `msg` de oferta esgotada.

### 8.3) Hold expirado (HOLD_EXPIRED)
1. No exporter, criar hold via `/api/v2/trader/purchase/preflight` (Authorization: Bearer peer-token).
2. Aguardar TTL (90s).
3. No importer, chamar `/api/v2/checkout/imported-offer/{importedOfferId}` com `Idempotency-Key` e `holdId`.
4. Esperado:
   - Resposta `status=REJECTED`, `reason=HOLD_EXPIRED`, `unitPrice` preenchido.

## 9) Validar UI via Playwright
- Garantir /etc/hosts com `xpory.localhost` apontando para 127.0.0.1.
- Usar a conta QA do item 8.0.

### 9.1) PRICE_CHANGED (UI)
```bash
PW_BASE_URL=http://xpory.localhost \
PW_ACCOUNT_ID=90805 \
PW_TOKEN=qa-preflight-token \
PW_OFFER_ID=80204 \
npx playwright test playwright/tests/preflight.price-changed.spec.ts
```
- Esperado: modal "O preco desta oferta foi atualizado. Atualize a pagina e tente novamente." + botao para recarregar.

### 9.2) OUT_OF_STOCK (UI)
- Preparar estoque zerado no exporter para a oferta originaria correspondente.
- Rodar o fluxo de compra no front (mesma oferta importada).
- Esperado: alerta "Oferta esgotada."

### 9.3) HOLD_EXPIRED (API)
Observacao: o fluxo UI dispara preflight + checkout no mesmo request, entao nao e viavel esperar o TTL pela UI.
Use o spec API:

```bash
PW_BASE_URL=http://xpory.localhost \
PW_IMPORTER_BASE_URL=http://localhost:8090 \
PW_EXPORTER_BASE_URL=http://localhost:8081 \
PW_CP_PEER_TOKEN=<peer-token> \
PW_REQUESTER_WL_ID=ee02f592-4b26-476c-8c68-404742c518b1 \
PW_REQUESTER_TRADER_ID=<trader-id> \
PW_ORIGIN_OFFER_ID=<origin-offer-id> \
PW_IMPORTED_OFFER_ID=<imported-offer-id> \
PW_EXPECTED_UNIT_PRICE=<preco> \
PW_HOLD_TTL_SECONDS=90 \
PW_CLIENT_BUYER_MASKED=qa***@xpory.local \
PW_QUANTITY=1 \
npx playwright test -c playwright/playwright.config.ts playwright/tests/preflight.hold-expired.api.spec.ts
```
- Esperado: `status=REJECTED`, `reason=HOLD_EXPIRED`.

- Validacoes CP:
  - Login em https://cp.localhost/admin/
  - Acessar /admin/trades
  - Abrir modal de detalhes (deve trazer oferta e comprador/vendedor, sem '-').

## Observacoes
- Caso as compras falhem com "Erro ao debitar em X$", verificar se a tabela `xtransaction_maturity` foi zerada indevidamente. Nesse caso, reestabelecer dados de maturidade (via processo de seeding/app) antes de repetir as compras.

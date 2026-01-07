# Runbook: Export policy - sem ofertas exportaveis

## Objetivo
Validar que nenhuma oferta e exportada quando a politica de exportacao limita a um conjunto sem ofertas ativas.

## Pre-requisitos
- Ambiente resetado via `reset-environment.md`.
- WLs ativos nas portas 8081 (exporter) e 8090 (importer).
- Snapshot de categorias e entidades sincronizados no CP.

## IDs usados (staging local)
- EXPORTER_WL_ID: `fd1a88a9-3a01-43bb-ae20-32618c1b3752`
- IMPORTER_WL_ID: `2a6f8e2c-874c-4476-aac3-0d535fb52ac7`
- CATEGORY_ID sem ofertas: `1029401` (activeCount=0)

## Passos
1) Atualizar o relacionamento exporter -> importer com a policy:

```bash
TOKEN=$(curl -sk -X POST https://cp.localhost/admin/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"master@xpory.local","password":"changeit"}' | python3 -c 'import json,sys;print(json.load(sys.stdin)["token"])')

curl -sk -X PUT "https://cp.localhost/admin/api/relationships/fd1a88a9-3a01-43bb-ae20-32618c1b3752/2a6f8e2c-874c-4476-aac3-0d535fb52ac7" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
    "status":"active",
    "fxRate":1,
    "limitAmount":100000,
    "export_policy":{
      "enabled":true,
      "min_created_at":"2000-01-01T00:00:00Z",
      "include_categories":["1029401"],
      "exclude_categories":[],
      "entity_allowlist":[],
      "entity_blocklist":[]
    },
    "updatedBy":"runbook",
    "updatedSource":"runbook"
  }'
```

2) Se o exporter continuar retornando ofertas, forcar o cache local (wl-01):

```bash
docker exec -i postgres-db psql -U postgres -d "wl-01" <<'SQL'
UPDATE cp_relationship_cache
SET export_policy='{"min_created_at":"2000-01-01T00:00:00Z","include_categories":["1029401"],"exclude_categories":[],"entity_allowlist":[],"entity_blocklist":[],"enabled":true}'
WHERE source_white_label_id='fd1a88a9-3a01-43bb-ae20-32618c1b3752'
  AND target_white_label_id='2a6f8e2c-874c-4476-aac3-0d535fb52ac7';
SQL
```

3) Validar no exporter:

```bash
curl -s "http://localhost:8081/api/v2/control-plane/export/offers?importerWlId=2a6f8e2c-874c-4476-aac3-0d535fb52ac7"
```

4) Gerar peer-token e sincronizar no importer:

```bash
curl -s -o /tmp/peer-token.json -w "%{http_code}\n" \
  --resolve cp.localhost:443:127.0.0.1 \
  --cacert /home/andre/Trabalho/XporY/repos/github/xpory-trader/traefik/certs/ca.crt \
  --cert /home/andre/Trabalho/XporY/repos/github/xpory-trader/traefik/certs/wl-client.p12:changeit \
  --cert-type P12 \
  -H 'Content-Type: application/json' \
  -d '{"targetWlId":"fd1a88a9-3a01-43bb-ae20-32618c1b3752","scopes":["offers:sync"]}' \
  https://cp.localhost/wls/2a6f8e2c-874c-4476-aac3-0d535fb52ac7/peer-token

TOKEN_PEER=$(python3 - <<'PY'
import json
print(json.load(open("/tmp/peer-token.json"))["token"])
PY
)

curl -s -X POST http://localhost:8090/api/v2/control-plane/debug/import \
  -H "Authorization: Bearer $TOKEN_PEER"
```

## Validacao (2026-01-06)
- Exporter: `total=0`.
- Importer: `summary.totalOffersDiscovered=0` e `summary.eligibleOffers=0`.

## Validado em 2026-01-07
- Cenario executado apos reset sem divergencias.

## Relacionados
- `reset-environment.md`

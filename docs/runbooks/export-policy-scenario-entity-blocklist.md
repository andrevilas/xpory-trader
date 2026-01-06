# Runbook: Export policy - blacklist de entidades

## Objetivo
Validar exportacao com exclusao de entidades especificas.

## Pre-requisitos
- Ambiente resetado via `cp-full-reset-and-trade-validation.md`.
- Snapshot de entidades sincronizado no CP.

## Passos
1) Selecionar uma entidade para bloquear `<ENTITY_ID>`.
2) Atualizar o relacionamento exporter -> importer com `entity_blocklist`:

```bash
curl -sk -X PUT "https://cp.localhost/admin/api/relationships/${EXPORTER_WL}/${IMPORTER_WL}" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
    "status":"active",
    "fxRate":1,
    "limitAmount":100000,
    "export_policy":{
      "enabled":true,
      "entity_allowlist":[],
      "entity_blocklist":["<ENTITY_ID>"],
      "min_created_at":"2026-01-01T00:00:00Z"
    },
    "updatedBy":"runbook",
    "updatedSource":"runbook"
  }'
```

3) Sincronizar ofertas no importer.
4) Validar que nenhuma oferta da entidade bloqueada foi importada.

## Validacoes
- `imported_offer` nao contem `entity_id=<ENTITY_ID>`.

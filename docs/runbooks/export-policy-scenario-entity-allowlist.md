# Runbook: Export policy - whitelist de entidades

## Objetivo
Validar exportacao restrita a entidades permitidas.

## Pre-requisitos
- Ambiente resetado via `cp-full-reset-and-trade-validation.md`.
- Snapshot de entidades sincronizado no CP.

## Passos
1) Selecionar uma entidade permitida `<ENTITY_ID>`.
2) Atualizar o relacionamento exporter -> importer com `entity_allowlist`:

```bash
curl -sk -X PUT "https://cp.localhost/admin/api/relationships/${EXPORTER_WL}/${IMPORTER_WL}" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
    "status":"active",
    "fxRate":1,
    "limitAmount":100000,
    "export_policy":{
      "enabled":true,
      "entity_allowlist":["<ENTITY_ID>"],
      "entity_blocklist":[],
      "min_created_at":"2026-01-01T00:00:00Z"
    },
    "updatedBy":"runbook",
    "updatedSource":"runbook"
  }'
```

3) Sincronizar ofertas no importer.
4) Validar que apenas ofertas da entidade whitelisted foram importadas.

## Validacoes
- `imported_offer` contem apenas `entity_id=<ENTITY_ID>`.

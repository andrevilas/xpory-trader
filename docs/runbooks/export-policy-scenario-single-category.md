# Runbook: Export policy - somente uma categoria permitida

## Objetivo
Validar exportacao limitada a uma unica categoria.

## Pre-requisitos
- Ambiente resetado via `cp-full-reset-and-trade-validation.md`.
- Snapshot de categorias sincronizado no CP.

## Passos
1) Selecionar uma categoria com ofertas ativas no exporter (ex.: `<CATEGORY_ID>`).
2) Atualizar o relacionamento exporter -> importer com `include_categories`:

```bash
curl -sk -X PUT "https://cp.localhost/admin/api/relationships/${EXPORTER_WL}/${IMPORTER_WL}" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
    "status":"active",
    "fxRate":1,
    "limitAmount":100000,
    "export_policy":{
      "enabled":true,
      "include_categories":["<CATEGORY_ID>"],
      "exclude_categories":[],
      "entity_allowlist":[],
      "entity_blocklist":[],
      "min_created_at":"2026-01-01T00:00:00Z"
    },
    "updatedBy":"runbook",
    "updatedSource":"runbook"
  }'
```

3) Executar a sincronizacao de ofertas no importer.
4) Validar que apenas ofertas da categoria selecionada foram importadas.

## Validacoes
- `imported_offer` contem apenas ofertas com `category_id` esperado.
- CP exibe a policy aplicada.

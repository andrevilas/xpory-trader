# Runbook: Export policy - excluir uma categoria

## Objetivo
Validar exportacao com exclusao de uma categoria especifica.

## Pre-requisitos
- Ambiente resetado via `reset-environment.md`.
- Snapshot de categorias sincronizado no CP.

## Passos
1) Selecionar uma categoria para excluir `<CATEGORY_ID>`.
2) Atualizar o relacionamento exporter -> importer com `exclude_categories`:

```bash
curl -sk -X PUT "https://cp.localhost/admin/api/relationships/${EXPORTER_WL}/${IMPORTER_WL}" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
    "status":"active",
    "fxRate":1,
    "limitAmount":100000,
    "export_policy":{
      "enabled":true,
      "include_categories":[],
      "exclude_categories":["<CATEGORY_ID>"],
      "min_created_at":"2026-01-01T00:00:00Z"
    },
    "updatedBy":"runbook",
    "updatedSource":"runbook"
  }'
```

3) Sincronizar ofertas no importer.
4) Validar que nenhuma oferta da categoria excluida foi importada.

## Validacao
- `imported_offer` nao contem `category_id=<CATEGORY_ID>`.

## Relacionados
- `reset-environment.md`

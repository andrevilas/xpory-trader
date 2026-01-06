# Runbook: Export policy - sem ofertas exportaveis

## Objetivo
Validar que nenhuma oferta e exportada quando a politica de exportacao remove todas as ofertas.

## Pre-requisitos
- Ambiente resetado via `cp-full-reset-and-trade-validation.md`.
- WL exporter/importer ativos.
- Snapshot de categorias e entidades sincronizados no CP.

## Passos
1) Identificar uma categoria e uma entidade com ofertas ativas no exporter.
2) Atualizar o relacionamento exporter -> importer com uma policy que exclui tudo:

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
      "entity_allowlist":[],
      "entity_blocklist":["<ENTITY_ID>"],
      "min_created_at":"2026-01-01T00:00:00Z",
      "include_domestic":false,
      "include_under_budget":false
    },
    "updatedBy":"runbook",
    "updatedSource":"runbook"
  }'
```

3) Executar a sincronizacao de ofertas no importer.
4) Validar que `imported_offer` nao recebeu ofertas (0 itens).

## Validacoes
- Endpoint de importacao retorna `count=0` ou tabela `imported_offer` vazia.
- CP mostra relacionamento ativo com `export_policy` presente.

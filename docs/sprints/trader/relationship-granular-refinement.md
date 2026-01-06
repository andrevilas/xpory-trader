# Relationship granular refinement (export policy)

## Contexto
Hoje a exportacao de ofertas segue um padrao amplo e deve virar fallback. A regra principal passa a ser definida pelo relacionamento exporter -> importer, incluindo criterios de exportacao. A exportacao sempre considera apenas ofertas ativas.

Objetivo: permitir que o Control Plane (xpory-trader) defina politicas de exportacao por relacionamento, com schema flexivel, e que o exporter (xpory-core) aplique esses filtros ao exportar.

## Regras base
- Ofertas exportadas devem estar ativas.
- Politica por relacionamento e direcional (exporter -> importer).
- O schema deve ser flexivel e documentado.
- Se a politica estiver ausente ou `enabled=false`, usar comportamento atual (fallback).

## Contrato do export_policy
### Campos suportados
```
export_policy: {
  enabled: boolean,
  min_created_at: string (ISO-8601, UTC, com sufixo 'Z'),
  price_min: decimal,
  price_max: decimal,
  include_categories: [string],
  exclude_categories: [string],
  include_domestic: boolean,
  include_under_budget: boolean,
  entity_allowlist: [string],
  entity_blocklist: [string],
  notes: string (opcional)
}
```

### Precedencia de filtros
1) `enabled=false` => ignora filtros (fallback para exportacao atual).
2) `entity_blocklist` tem prioridade sobre allowlist.
3) `include_categories` e allowlist; se vazio/nulo, nao restringe.
4) `exclude_categories` sempre remove mesmo se estiver na allowlist.
5) `price_min/price_max` sao limites inclusivos.
6) `min_created_at` inclui apenas ofertas criadas a partir da data (interpretar em UTC; exigir sufixo `Z`).
7) `include_domestic` e `include_under_budget` aplicam filtro booleano quando definidos.
8) `entity_allowlist` e `include_categories` se acumulam (ambos aplicam).

### Exemplo A (categoria + data)
```json
{
  "export_policy": {
    "enabled": true,
    "min_created_at": "2024-12-01T00:00:00Z",
    "include_categories": ["SERVICOS", "BEBIDAS"]
  }
}
```

### Exemplo B (faixa de valor + blacklist)
```json
{
  "export_policy": {
    "enabled": true,
    "price_min": 50.0,
    "price_max": 200.0,
    "entity_blocklist": ["ent-99", "ent-101"]
  }
}
```

### Exemplo C (whitelist + exclusao de categoria)
```json
{
  "export_policy": {
    "enabled": true,
    "entity_allowlist": ["ent-10", "ent-11"],
    "exclude_categories": ["OUTLET"]
  }
}
```

## Workplan por componente (checklist)

### 1) Control Plane (xpory-trader)
- [ ] 1.1 Modelo de dados: adicionar `exportPolicy` no Relationship seguindo padrao atual de policy.
- [ ] 1.2 API de relacionamento: `PUT /relationships/{src}/{dst}` aceita `export_policy`.
- [ ] 1.3 API de relacionamento: `GET /relationships/{src}/{dst}` retorna `export_policy` + assinatura.
- [ ] 1.4 Listagens incluem `export_policy` opcional.
- [ ] 1.5 Assinatura: `export_policy` incluido no payload assinado; mudanca de schema exige revisao da assinatura.
- [ ] 1.6 Compatibilidade: clientes antigos devem ignorar campos desconhecidos.
- [ ] 1.7 Validacao: `min_created_at` ISO-8601 com `Z`.
- [ ] 1.8 Validacao: `price_min <= price_max` quando definidos.
- [ ] 1.9 Validacao: arrays validos para categorias e entidades.
- [ ] 1.10 Politica invalida => log + fallback.
- [ ] 1.11 Telemetria: evento `RELATIONSHIP_EXPORT_POLICY_UPDATED`.
- [ ] 1.12 Snapshot categorias: tabela + `GET /wls/{id}/offer-categories`.
- [ ] 1.13 Snapshot categorias: `POST /wls/{id}/offer-categories/sync` + job periodico.
- [ ] 1.14 Snapshot categorias: paginacao `limit=200`, `max=1000`, busca por nome.
- [ ] 1.15 Snapshot entidades: tabela + `GET /wls/{id}/entities` (busca + `activeOnly`).
- [ ] 1.16 Snapshot entidades: `POST /wls/{id}/entities/sync` + job periodico.
- [ ] 1.17 Snapshot entidades: paginacao `limit=200`, `max=1000`, busca por nome.
- [ ] 1.18 UI: console de problemas no modal do relacionamento.
- [ ] 1.19 UI: avisar itens inexistentes no snapshot com sugestao (remover ou ressincronizar).

---

### 2) Exporter (xpory-core)
- [ ] 2.1 Endpoint `GET /api/v2/control-plane/offer-categories` retorna `{ categoryId, name, activeCount }`.
- [ ] 2.2 Endpoint `offer-categories` protegido por auth CP (JWT + mTLS).
- [ ] 2.3 Endpoint `GET /api/v2/control-plane/entities` retorna `{ entityId, name, activeOffersCount, status, updatedAt }`.
- [ ] 2.4 Endpoint `entities` suporta `updatedSince`, `activeOnly`, `name`.
- [ ] 2.5 Endpoint `entities` com paginacao `limit=200`, `max=1000`.
- [ ] 2.6 Endpoint `entities` protegido por auth CP (JWT + mTLS).
- [ ] 2.7 Enforcement: aplicar `export_policy` ao export (sempre ofertas ativas).
- [ ] 2.8 Enforcement: precedencia conforme regras do contrato.
- [ ] 2.9 Enforcement: `include_domestic` e `include_under_budget` vindos do dominio da oferta.
- [ ] 2.10 Fallback: politica ausente/`enabled=false`/invalida => log + fallback.
- [ ] 2.11 Sync incremental: usar `updatedSince` se `updatedAt` confiavel.
- [ ] 2.12 Sync incremental: se `updatedAt` nao confiavel, responder full sync com log.
- [ ] 2.13 Observabilidade: logs/metricas de ofertas filtradas.
- [ ] 2.14 Observabilidade: alerta opcional para politica resultando em 0 ofertas.

---

### 3) UI / Admin (Control Plane)
- [ ] 3.1 Formulario: categorias, faixa de preco, data minima, flags booleanas.
- [ ] 3.2 Formulario: allowlist/blocklist de entidades com autocomplete via CP.
- [ ] 3.3 (Opcional) Impacto: exibir ativas vs elegiveis pela politica.

## Checklist de migracao e validacao
- [ ] Migracao: adicionar `export_policy` seguindo padrao atual de policy.
- [ ] Migracao: default `null`.
- [ ] Validacao CP: `min_created_at` parseavel, com `Z` e UTC.
- [ ] Validacao CP: tipos e faixas basicas.
- [ ] Validacao Exporter: politica invalida => log + fallback.
- [ ] Validacao Exporter: garantir filtro por ofertas ativas.
- [ ] Testes CP: update/fetch com assinatura e validacoes.
- [ ] Testes Exporter: filtros por categoria/preco/data/entidade e precedencia.
- [ ] Testes Exporter: fallback para politica ausente/`enabled=false`/invalida.

## Rollout
- [ ] Deploy CP com schema + APIs.
- [ ] Deploy exporter com enforcement atras de feature flag.
- [ ] Habilitar por relacionamento gradualmente.

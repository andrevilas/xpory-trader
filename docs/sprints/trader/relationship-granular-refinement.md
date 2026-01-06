# Relationship granular refinement (export policy)

## Contexto
Hoje a exportacao de ofertas segue um padrao amplo e deve virar fallback. A regra principal passa a ser definida pelo relacionamento exporter -> importer, incluindo criterios de exportacao. A exportacao sempre considera apenas ofertas ativas.

Objetivo: permitir que o Control Plane (xpory-trader) defina politicas de exportacao por relacionamento, com schema JSON flexivel, e que o exporter (xpory-core) aplique esses filtros ao exportar.

## Regras base
- Ofertas exportadas devem estar ativas.
- Politica por relacionamento e direcional (exporter -> importer).
- O schema deve ser flexivel (JSON) e documentado.
- Se a politica estiver ausente ou `enabled=false`, usar comportamento atual (fallback).

## Contrato do export_policy
### Campos suportados (JSON)
```
export_policy: {
  enabled: boolean,
  min_created_at: string (ISO-8601),
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
6) `min_created_at` inclui apenas ofertas criadas a partir da data.
7) `include_domestic` e `include_under_budget` aplicam filtro booleano quando definidos.

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

## Workplan por componente

### 1) Control Plane (xpory-trader)
**1.1 - Modelo de dados**
- Adicionar campo JSON `exportPolicy` no Relationship (ou tabela 1:1).
- Persistir sem schema rigido, com validacoes basicas.

**1.2 - API de relacionamento**
- `PUT /relationships/{src}/{dst}` aceita `export_policy`.
- `GET /relationships/{src}/{dst}` retorna `export_policy` + assinatura.
- Listagens de relacionamento devem incluir `export_policy` opcional.

**1.3 - Validacoes**
- `min_created_at` ISO-8601 valido.
- `price_min <= price_max` quando ambos definidos.
- Arrays validos para categorias e entidades.

**1.4 - Telemetria**
- Emitir evento `RELATIONSHIP_EXPORT_POLICY_UPDATED`.

**1.5 - Categorias do exporter (snapshot)**
- Criar snapshot de categorias do exporter com quantidade de ofertas ativas.
- Endpoint CP: `GET /wls/{id}/offer-categories`.
- Job de sync: `POST /wls/{id}/offer-categories/sync` + job periodico.

---

### 2) Exporter (xpory-core)
**2.1 - Endpoint de categorias**
- `GET /api/v2/control-plane/offer-categories`.
- Retorna lista de categorias com `activeCount`.

**2.2 - Aplicacao da politica no export**
- Aplicar filtros do `export_policy` ao montar o payload de exportacao.
- Sempre filtrar por ofertas ativas.
- Precedencia conforme regras acima.
- Fallback para exportacao atual se politica ausente ou `enabled=false`.

**2.3 - Observabilidade**
- Logs e metricas para quantidade de ofertas filtradas.
- Alerta opcional quando politica resulta em 0 ofertas.

---

### 3) UI / Admin (Control Plane)
**3.1 - Configuracao da politica**
- Campos para categoria, faixa de preco, data minima, flags booleanas.
- Allowlist/blocklist de entidades (chips + autocomplete).

**3.2 - Visibilidade de impacto (opcional)**
- Mostrar total de ofertas ativas vs elegiveis pela politica.

## Checklist de migracao e validacao

**Migracao**
- Adicionar coluna JSON `export_policy` (ou tabela auxiliar).
- Default `null` (politica ausente).

**Validacoes CP**
- Schema basico valido (tipos e faixas).
- `min_created_at` parseavel.

**Validacoes Exporter**
- Politica invalida => log + fallback.
- Garantir filtro por ofertas ativas.

**Testes**
- CP: update/fetch com assinatura e validacoes.
- Exporter: filtros por categoria/preco/data/entidade e precedencia.
- Fallback: politica ausente ou `enabled=false`.

## Rollout
- Deploy CP com schema + APIs.
- Deploy exporter com enforcement atras de feature flag.
- Habilitar por relacionamento gradualmente.

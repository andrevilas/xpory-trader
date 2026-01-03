# How-To Trading (Control Plane)

Guia prático para gerenciar o comércio entre White Labels (WLs) no Control Plane.

Este documento descreve, passo a passo, como configurar e operar o comércio
(offers, políticas, relacionamentos e importação/exportação) entre WLs.

Estado atual consolidado da sprint trader: `docs/sprints/trader/state-of-sprint.md`.

---

## Conceitos-chave (resumo rápido)

- **WL (White Label)**: tenant/cliente operando sua própria instância.
- **Relationship**: autoriza operação entre dois WLs (status, fx, limites).
- **Policy**:
  - `importEnabled`: permite importar ofertas.
  - `exportEnabled`: permite exportar ofertas.
  - `visibilityEnabled`: permite tornar o WL visível.
  - `visibilityWls`: lista de WLs permitidos (lista **restritiva**).
  - `exportDelayDays`: atraso para exportação.
- **Trader Account**: identidade operacional do WL.

**Regra geral**:
- **Visibilidade** controla descoberta/listagem.
- **Relationship ativo** controla autorização transacional.

---

## Exemplo base (3 WLs)

Vamos usar 3 WLs fictícios:
- **WLA = alpha**
- **WLB = beta**
- **WLC = gamma**

### Objetivo principal
- WLB importa ofertas do WLA
- WLC importa ofertas do WLB
- WLA e WLC **não** negociam entre si

---

## Checklist mínimo (antes de tudo)

1) **WLs cadastrados** no CP (com status `active`)
2) **Gateway URL** correto para cada WL (apontando para o backend, não para o frontend)
3) **mTLS/JWKS OK** entre CP e WL
4) **Trader Account** configurado e ativo

---

## Passo 1 — Configurar Relationships

### Relationship A -> B (alpha -> beta)
- `status`: `active`
- `fxRate`: `1.0`
- `limitAmount`: `100000`

### Relationship B -> C (beta -> gamma)
- `status`: `active`
- `fxRate`: `1.0`
- `limitAmount`: `100000`

### Relationship A -> C (alpha -> gamma)
- `status`: `inactive` ou **não criar**

**Observação**: Relationship é direcional (source/target).

---

## Passo 2 — Configurar Policies

### Policy do WLA (exportador para WLB)

- `exportEnabled = true`
- `importEnabled = false` (opcional)
- `visibilityEnabled = true`
- `visibilityWls = ["beta"]`
- `exportDelayDays = 0` (sem atraso)

### Policy do WLB (importa do WLA e exporta para WLC)

- `importEnabled = true`
- `exportEnabled = true`
- `visibilityEnabled = true`
- `visibilityWls = ["alpha", "gamma"]`
- `exportDelayDays = 0`

### Policy do WLC (importa do WLB)

- `importEnabled = true`
- `exportEnabled = false`
- `visibilityEnabled = true`
- `visibilityWls = ["beta"]`
- `exportDelayDays = 0`

**Importante**: `visibilityWls` é **lista restritiva**.
Se estiver vazia, **ninguém será consultado** para importação.

---

## Passo 3 — Verificar sincronização

1) Execute o sync de políticas no WL (job ou manual).
2) Verifique se a política chegou (`/policies/pull`).
3) Verifique se os WLs visíveis aparecem na cache local.

---

## Cenários comuns

### Cenário A — Todos negociam com todos

- Relationships ativos entre todos os pares.
- Policies com `visibilityEnabled = true`.
- `visibilityWls` contendo **todos os WLs**.

### Cenário B — Apenas um exportador central

- WLA exporta para todos.
- WLB e WLC apenas importam.
- Relationships:
  - WLA -> WLB (active)
  - WLA -> WLC (active)
- Policies:
  - WLA: `exportEnabled=true`, `visibilityWls=["beta","gamma"]`
  - WLB/WLC: `importEnabled=true`, `visibilityWls=["alpha"]`

### Cenário C — Acesso restrito (parcerias privadas)

- Apenas parceiros explícitos em `visibilityWls`.
- Relationships ativos apenas entre parceiros.

### Cenário D — WL suspenso

- Relationship status = `paused` ou `inactive`.
- Policy do WL pode permanecer ativa, mas relação bloqueia operação.

---

## Troubleshooting (problemas frequentes)

### 1) Importação não ocorre
- Verifique se o **WL importador** tem `importEnabled=true`.
- Verifique se o **WL exportador** tem `exportEnabled=true`.
- Verifique se `visibilityWls` inclui o WL correto.
- Relationship ativo entre source/target.

### 2) WL aparece na lista mas não importa
- `visibilityEnabled=true` mas `relationship` não está ativo.

### 3) Policy não chega no WL
- `controlPlane.baseUrl` correto (geralmente inclui `/cpservice`).
- Certificados mTLS corretos.
- Verificar erro 401/403/502 no CP.

### 4) Exportação atrasada
- `exportDelayDays > 0`.

---

## Checklist final (produção)

- [ ] WLs ativos
- [ ] Gateway URL correto
- [ ] Trader account ativo
- [ ] Policies alinhadas
- [ ] Relationships ativos
- [ ] Logs do CP sem erro
- [ ] Importação validada em staging

---

## Glossário rápido

- **Importador**: WL que consome ofertas de outro.
- **Exportador**: WL que fornece suas ofertas.
- **Visibility**: controla descoberta/listagem.
- **Relationship**: controla autorização e regras de negócio.

---

FIM

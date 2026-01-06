# Runbook: Subida do ambiente (Commercial Balance)

## Objetivo
Documentar como subir o ambiente end-to-end para validar a balanca comercial (backend + WL + Admin UI), incluindo checagens essenciais, problemas encontrados e resolucoes.

## Escopo
- xpory-trader (Control Plane - CP)
- xpory-core (WL)
- xpory-trader-admin-ui (Admin UI)

## Pre-requisitos
- Git configurado e acesso aos repositorios.
- Java e Gradle (via wrapper) para xpory-trader e xpory-core.
- Node.js + npm/yarn para o Admin UI.
- Acesso ao gateway/CP (quando aplicavel) ou proxy local `/admin/api` configurado.

## Repositorios
- CP: `/home/andre/Trabalho/XporY/repos/github/xpory-trader`
- WL: `/home/andre/Trabalho/XporY/repos/xpory-core`
- Admin UI: `/home/andre/Trabalho/XporY/repos/xpory-trader-admin-ui`

## Passos

### 1) Control Plane (xpory-trader)
1. Entrar no repo:
   - `cd /home/andre/Trabalho/XporY/repos/github/xpory-trader`
2. Rodar testes unitarios principais:
   - `./gradlew test --tests cpservice.ReportServiceSpec`
3. Rodar suite completa:
   - `./gradlew test`

### 2) WL (xpory-core)
1. Entrar no repo:
   - `cd /home/andre/Trabalho/XporY/repos/xpory-core`
2. Rodar testes unitarios especificos:
   - `./gradlew test --tests xpory.web.controlplane.TraderPurchaseTelemetryServiceSpec`
3. Rodar suite completa:
   - `./gradlew test`

### 3) Admin UI (xpory-trader-admin-ui)
1. Entrar no repo:
   - `cd /home/andre/Trabalho/XporY/repos/xpory-trader-admin-ui`
2. Rodar suite de testes:
   - `npm test`

## Validacao

### API / Relatorios (CP)
- Confirmar que o endpoint `/reports/trade-balance` responde com:
  - Pairs bidirecionais (wlAId, wlBId)
  - Totais consolidados (totalExported, totalImported, balance)
  - Direcoes (aToB/bToA) e availability flags

### Telemetria (WL -> CP)
- Confirmar que eventos `TRADER_PURCHASE` contem:
  - `originWhiteLabelId` / `targetWhiteLabelId`
  - `wlExporterId` / `wlImporterId`
  - `unitPrice`, `requestedQuantity` / `confirmedQuantity`

### Admin UI
- Ao selecionar WL origem, a lista de WL destino deve mostrar apenas pares reciprocos.
- O grafico de barras (timeline) deve refletir saldo positivo/negativo por periodo.
- Totais de exportacao/importacao devem bater com o CP.

## Problemas encontrados e resolucoes

### 1) Gradle wrapper sem permissao para lock em ~/.gradle
**Sintoma:**
- Erro ao rodar `./gradlew test` no xpory-trader:
  - `FileNotFoundException: .../gradle-4.5-bin.zip.lck (Permissao negada)`

**Causa provavel:**
- Permissao insuficiente para criar/lockar arquivos do Gradle no home.

**Solucao aplicada:**
- Executar com permissao adequada para acesso ao `~/.gradle`.
- Reexecutar `./gradlew test` apos liberar o acesso.

### 2) Warns de configuracao e env vars no xpory-core
**Sintoma:**
- Logs de avisos durante testes: variaveis de ambiente ausentes e integracoes nao configuradas.

**Impacto:**
- Nao bloqueiam os testes unitarios/integracao. Sao warnings esperados para ambiente local.

**Solucao aplicada:**
- Nenhuma, apenas registro. Opcionalmente definir env vars conforme necessario para ambientes mais proximos de producao.

### 3) Warnings do React Router nos testes do Admin UI
**Sintoma:**
- Warns sobre `future flags` do React Router v7 durante `npm test`.

**Impacto:**
- Nao afeta execucao dos testes. Suite passou com sucesso.

**Solucao aplicada:**
- Nenhuma; warnings informativos.

## Observacoes
- Para validacao manual, utilizar o Admin UI e verificar:
  - Seletores de parceiros com reciprocidade
  - Timeline e saldo consolidado (positivo/negativo)
  - Diferenca entre exportado/importado consistente com os eventos

## Historico
- 2026-01-06: Runbook criado e validado com as suites de teste acima.

## Relacionados
- `staging-environment-setup.md`
- `reset-environment.md`

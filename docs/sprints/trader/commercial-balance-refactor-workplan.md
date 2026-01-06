# Commercial Balance Refactor Workplan

## Contexto
A tela de balanco comercial deve permitir identificar deficit/superavit entre dois Parceiros. Isso so e valido quando existem relacoes mutuas de importacao/exportacao entre as duas WLs, independente da politica aplicada em cada relacao. Atualmente o fluxo aparenta expor apenas uma relacao unidirecional, o que pode mascarar o saldo real.

## Problema
- A apresentacao atual parece derivar de apenas um relacionamento (direcao unica).
- O saldo real depende do conjunto bidirecional (A -> B e B -> A).
- Isso impede o usuario de identificar deficit/superavit quando apenas uma direcao esta sendo considerada.

## Objetivo
Garantir que o balanco comercial seja calculado e apresentado a partir das relacoes mutuas (bidirecionais), permitindo visualizar deficit/superavit de forma correta.

## Escopo
- Ajustar calculo e/ou API do balanco comercial para consolidar as duas direcoes.
- Ajustar payloads para deixar claro a direcionalidade e o saldo consolidado.
- Manter compatibilidade com filtros atuais (WLs, periodo, status).
- Admin UI (xpory-trader-admin-ui):
  - Selecao de dois parceiros com relacoes mutuas.
  - Ao selecionar o primeiro parceiro, filtrar o segundo apenas para pares com relacao reciproca.
  - Atualizar o grafico para um formato mais adequado (ex.: barras verticais em ordem cronologica).

## Fora do escopo
- Revisao das politicas de exportacao em si.

## Hipoteses
- Existe endpoint/servico atual que gera o balanco comercial a partir de relacionamentos.
- As relacoes mutuas sao identificadas pelo par (wlExporter, wlImporter).

## Plano de Trabalho
### Backend (xpory-trader)
1. Mapear fluxo atual (servico/controller/repository) do balanco comercial.
2. Identificar onde a relacao unidirecional e usada no calculo.
3. Definir modelo de agregacao bidirecional:
   - Par canonico: ordenar ids (min/max) para agrupar A<->B.
   - Campos de entrada: valores A->B e B->A.
   - Saldo final: (A->B) - (B->A).
4. Atualizar DTO/response para expor:
   - wlExporterId, wlImporterId (ou par canonico + direcao)
   - totalExportado, totalImportado
   - saldo (positivo = superavit, negativo = deficit)
   - flags de disponibilidade (ex.: hasExporterToImporter, hasImporterToExporter).
5. Ajustar endpoint para suportar consultas por par reciproco (A,B) e retornar serie temporal consolidada.
6. Revisar permissao/seguranca e filtros (periodo, status, parceiro).
7. Testes:
   - Unitarios do service de balanco.
   - Integracao do endpoint.
   - Cenarios: somente A->B, somente B->A, ambos, sem dados.

### Frontend (xpory-trader-admin-ui)
1. Mapear tela atual de balanco comercial (componentes, hooks, api).
2. Ajustar selecao de parceiros:
   - Dropdown 1: listar parceiros com pelo menos uma relacao.
   - Ao selecionar parceiro A, carregar apenas parceiros B com relacao reciproca A<->B.
   - Bloquear selecao de pares invalidos e exibir mensagem orientativa.
3. Ajustar chamadas de API conforme novo contrato (par reciproco + serie temporal).
4. Substituir grafico atual por barras verticais em ordem cronologica:
   - Eixo X: datas (periodo selecionado).
   - Eixo Y: saldo (positivo = superavit, negativo = deficit).
   - Cores distintas para positivo/negativo.
5. Ajustar legendas e textos explicativos na UI.
6. Testes:
   - Unitarios (formatacao, filtros, mapeamento de serie).
   - E2E/visual (quando aplicavel) para selecao e grafico.

## Registro de Avancos
Use esta secao para marcar progresso e manter historico.

### Checklist
- [ ] Backend: mapeamento de fluxo atual
- [ ] Backend: modelo de agregacao bidirecional definido
- [ ] Backend: DTO/response atualizado
- [ ] Backend: endpoint ajustado
- [ ] Backend: testes atualizados
- [ ] Frontend: tela mapeada
- [ ] Frontend: selecao de parceiros reciprocos
- [ ] Frontend: grafico de barras cronologico
- [ ] Frontend: textos/legendas ajustados
- [ ] Frontend: testes atualizados
- [ ] Validacao em staging

### Diario de Progresso
- YYYY-MM-DD: descricao objetiva do que foi feito + link/commit se existir.

## Criterios de Aceite
- Para um par com relacoes mutuas, o balanco reflete a diferenca entre as duas direcoes.
- Para um par sem reciprocidade, o saldo indica corretamente a inexistencia da relacao oposta (ex.: 0 do lado ausente) e o deficit/superavit resultante.
- Na UI, o segundo parceiro so apresenta opcoes com relacao reciproca ao primeiro.
- O grafico exibe a evolucao cronologica do saldo em barras verticais.
- Nao ha regressao nos filtros atuais.

## Testes
- Unitarios do service de balanco.
- Integracao do endpoint (quando existir).
- Cenarios: somente A->B, somente B->A, ambos.

## Riscos
- Mudanca no contrato pode exigir ajuste do Admin UI.
- Dados legados sem par reciproco podem expor saldo parcial (precisa ser claramente comunicado).

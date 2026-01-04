# Evolutions — Caminhos para sincronizacao de ofertas (CP x WL)

Este documento registra possibilidades para evoluir a sincronizacao de ofertas
entre WLs, considerando o papel do Control Plane (CP) e o impacto em WL
exportadores/importadores. O objetivo e comparar caminhos com prós e contras
sem definir uma decisao final.

---

## 1) Contexto atual (resumo)

- O CP mantem politicas e relacionamentos (governanca) e emite tokens.
- Cada WL importador executa o import sync e puxa as ofertas diretamente do WL
  exportador.
- O exportador responde com o snapshot de ofertas exportaveis e o importador
  aplica elegibilidade local (relacionamento, visibilidade, export_delay, status).

**Caracteristica chave:** a carga no exportador cresce conforme o numero de
importadores (pull N×), especialmente em WLs com alto volume de ofertas.

---

## 2) Alternativas arquiteturais

### Alternativa A — CP como data plane (snapshot batch)

**Descricao**
- O CP faz o pull do exportador em janelas definidas (ex.: diario / a cada X horas).
- O CP armazena a saida raw (sem alterar formato) e a entrega aos importadores.
- Os WLs importadores continuam aplicando elegibilidade e persistindo o snapshot
  localmente.

**Fluxo resumido**
- Importador consulta CP para obter snapshot raw.
- CP serve a versao mais recente por exportador.
- Importador aplica elegibilidade e persiste localmente.

**Pros**
- Reduz carga nos WLs exportadores (pull unico por janela).
- Padroniza o snapshot para todos os importadores.
- Mais controle operacional (janelas, retries, backoff, observabilidade).

**Contras**
- CP vira ponto central de falha para sync.
- CP precisa de storage/bandwidth e governanca de retention.
- CP passa a armazenar dados de ofertas (impacto de compliance).

---

### Alternativa B — CP como data plane + eventos incrementais

**Descricao**
- CP captura snapshot base e depois distribui eventos por oferta para cada
  importador elegivel.
- Importadores processam delta e atualizam o catalogo local.

**Fluxo resumido**
- CP puxa snapshot base e publica versao inicial.
- CP emite eventos de alteracao por oferta.
- Importadores aplicam delta e mantem consistencia local.

**Pros**
- Reduz custo de snapshots completos.
- Menor latencia de propagacao.

**Contras**
- Alto fan-out de eventos (cada alteracao pode gerar N envios).
- Maior complexidade: ordenacao, idempotencia, retries, backpressure.
- Exige pipeline de eventos resiliente e observabilidade mais sofisticada.

---

### Alternativa C — Atual (WL -> WL) com mitigacoes

**Descricao**
- Mantem o modelo atual de importador puxando do exportador.
- Adiciona mitigacoes: cache curto, jitter, ETag/If-Modified-Since, throttling.

**Fluxo resumido**
- Importador puxa snapshot direto do exportador.
- Exportador responde com ofertas exportaveis.
- Importador aplica elegibilidade e persiste localmente.

**Pros**
- Menor mudanca arquitetural.
- Mantem o exportador como fonte unica de dados.

**Contras**
- Continua escalando mal com N importadores.
- Exportadores de alto volume permanecem sobrecarregados.

---

### Alternativa D — CP orquestra um pool de sincronizacoes

**Descricao**
- O WL importador nao chama o exportador diretamente.
- O WL consulta o CP para entrar em um pool/filas de sincronizacao.
- O CP controla quando cada WL deve executar o sync (janela, rate limit, ordem),
  e notifica o importador quando e sua vez de solicitar a sincronizacao ao
  exportador.

**Fluxo resumido**
- Importador registra pedido no CP.
- CP agenda, aplica rate limit e libera o slot.
- Importador executa sync direto no exportador.

**Pros**
- Reduz picos de carga nos exportadores sem mudar a fonte de dados.
- Mantem o exportador como fonte unica (sem storage de raw no CP).
- Permite governanca central de concorrencia e janelas.

**Contras**
- Ainda existe o pull N× (apenas mais espaçado).
- Adiciona dependencia operacional do CP para coordenar o sync.
- Exige um canal de notificacao/heartbeat entre CP e WLs.
- Maior complexidade de estado (fila, expiracao, retries, fairness).

**Detalhamento do pool (possivel desenho)**
- **Modelo de fila**: global, por exportador, ou por par WL (importador+exportador).
- **Rate limit**: token bucket por exportador para suavizar concorrencia.
- **Fairness**: round-robin por importador, ou prioridade por SLA.
- **TTL de pedido**: se o WL nao iniciar o sync no prazo, o slot expira.
- **Retries**: re-enfileirar em falhas transitorias com backoff.
- **Canal de sinalizacao**: polling, webhook, long-poll ou fila.

---

## 2.1) Tabela comparativa (alto nivel)

| Alternativa | Latencia | Custo exportador | Custo CP | Complexidade | Risco operacional |
|-------------|----------|------------------|----------|--------------|-------------------|
| A (CP batch) | Media (horas) | Baixo | Medio/alto | Media | Medio (CP central) |
| B (CP + eventos) | Baixa | Baixo | Alto | Alta | Alto |
| C (atual + mitigacoes) | Media | Alto | Baixo | Baixa | Baixo |
| D (pool CP) | Media | Medio | Baixo/medio | Media | Medio |

Notas:
- “Custo exportador” considera CPU, IO e requests originadas por N importadores.
- “Custo CP” considera storage, banda e orquestracao.
- Latencia assume janelas de sync por horas e sem necessidade de real-time.

---

## 3) Refinamento de politicas (filtro por categoria e outras regras)

Independente do caminho escolhido, podemos evoluir a governanca para permitir
filtros adicionais no snapshot importado. Exemplo inicial:

- **Categorias permitidas**: importar apenas ofertas de categorias explicitamente
  autorizadas por politica.

**Impacto esperado**
- Reduz volume de dados sincronizados.
- Melhora relevancia do catalogo importado.
- Diminui custo de processamento local.

**Observacoes**
- Precisa de contrato claro no CP (campo de politica) e no WL (aplicacao do filtro).
- Pode ser combinado com outras regras futuras (ex.: tags, faixa de preco,
  disponibilidade minima, cobertura geografica).

---

## 4) Pontos em aberto

- Frequencia ideal do snapshot (horas vs diario).
- Retention no CP (dias/versoes).
- Fallback para pull direto do exportador se snapshot do CP estiver expirado.
- Metricas de custo/latencia para comparar as alternativas.
- Como medir custo operacional do pool de sincronizacao (Alternativa D).
- Qual o canal preferido de sinalizacao entre CP e WLs (polling vs push).

---

## 5) Proximos passos sugeridos (nao vinculantes)

- Estimar custo operacional do CP como data plane (storage, throughput, retention).
- Definir payload minimo para armazenamento raw (sem quebra de compatibilidade).
- Prototipar o filtro de categorias em politica (CP + WL).
- Simular backlog e fairness do pool de sincronizacao (Alternativa D).
- Produzir uma matriz de decisao com pesos (custo, latencia, risco, complexidade).

---

## 6) Sugestao tecnica (nao vinculante)

Considerando latencia aceitavel em horas, CP com banda/storage, poucos
exportadores e muitos importadores, a melhor relacao custo/beneficio tende a ser:

- **Comecar pela Alternativa A (CP batch)** como base principal.
- **Adicionar mitigacoes da Alternativa C** (ETag/If-Modified-Since, jitter) para
  reduzir desperdicio de transferencias e evitar picos.
- **Manter fallback** para WL -> WL quando o CP nao tiver snapshot recente.
- **Introduzir o filtro de categorias** como primeiro refinamento de politica,
  reduzindo volume e custo de processamento.

A Alternativa D (pool no CP) pode ser adotada como plano intermediario caso o
CP precise reduzir picos sem assumir armazenamento raw. A Alternativa B deve
ser reservada para quando houver necessidade real de quase real-time.

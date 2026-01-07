# Runbook: Balança Comercial - UI

Data base: 2026-01-06 (BRT)

## Objetivo
Validar os valores exibidos na tela de balanca comercial com base nos trades gerados.

## Pre-requisitos
- Trades criados (CONFIRMED/PENDING/REJECTED).
- Admin UI acessivel.

## Passos (Playwright manual)
1) Abrir `https://cp.localhost/admin/commercial-balance` e autenticar.
2) Selecionar `WL origem` = `wl-exporter`, `WL destino` = `wl-importer`.
3) Clicar em **Buscar**.
4) Validar:
   - Status do relacionamento, FX e limite.
   - Contagens de Confirmadas/Pendentes/Rejeitadas.
   - Totais em valores consistentes com o endpoint `/admin/api/reports/trade-balance`.

## Validacao
- Contagens e valores na tela batem com o API.
- Grafico e acumulados aparecem.

## Validado em 2026-01-07
- Tela carregou resumo e totais apos reset.

## Relacionados
- `commercial-balance-env-setup.md`

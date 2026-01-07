# Runbook: Aprovar/Rejeitar Trades - UI

Data base: 2026-01-06 (BRT)

## Objetivo
Validar o fluxo de aprovacao e rejeicao de trades no Admin UI e a atualizacao dos status.

## Pre-requisitos
- Trades pendentes criados via `reset-environment.md`.
- Admin UI acessivel em `https://cp.localhost/admin/`.
- Usuario MASTER ativo.

## Passos (Playwright manual)
1) Abrir `https://cp.localhost/admin/trades/pending` e autenticar.
2) Validar que existem trades pendentes.
3) Abrir **Ver detalhes** em um trade e confirmar:
   - Oferta, comprador e vendedor preenchidos (sem `—`).
4) Aprovar um trade e rejeitar outro (motivo `MANUAL_REJECT`).
5) Recarregar a lista e validar decremento de pendentes.

## Validacao
- Detalhes do trade exibem oferta + cliente + contatos.
- Aprovado muda para `CONFIRMED`, rejeitado para `REJECTED`.
- Pendentes reduzidos.

## Validado em 2026-01-07
- Aprovar/rejeitar atualiza contagem e status.

## Relacionados
- `reset-environment.md`
- `admin-ui.md`

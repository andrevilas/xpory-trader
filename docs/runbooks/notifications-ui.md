# Runbook: Notificacoes - UI

Data base: 2026-01-06 (BRT)

## Objetivo
Validar o fluxo de notificacoes no Admin UI para eventos de trade.

## Pre-requisitos
- Trades gerados (pendentes/aprovados/rejeitados).
- Admin UI acessivel.

## Passos (Playwright manual)
1) Abrir `https://cp.localhost/admin/notifications` e autenticar.
2) Verificar lista de notificacoes recentes.
3) Confirmar eventos do tipo **Novo trade** e **Trade pendente**.
4) Opcional: apos aprovar/rejeitar um trade, clicar em **Atualizar** e validar novos eventos.

## Validacao
- Lista mostra eventos recentes com hora e WLs.
- Contador de nao lidas atualiza conforme novas notificacoes.

## Validado em 2026-01-07
- Lista carregou e clique levou para negociacoes.

## Relacionados
- `notifications.md`

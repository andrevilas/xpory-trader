# Runbooks - XPORY Trader

Este diretorio organiza runbooks por fluxo principal e cenarios de validacao.

## Fluxos principais

1) **Subir ambiente staging (CP + WL + Admin UI)**
   - [staging-environment-setup.md](staging-environment-setup.md)

2) **Reset completo do ambiente local (CP + WLs)**
   - [reset-environment.md](reset-environment.md)

3) **Admin UI com mTLS via Traefik (local)**
   - [admin-ui.md](admin-ui.md)

4) **Validacoes end-to-end (balanca comercial)**
   - [commercial-balance-env-setup.md](commercial-balance-env-setup.md)
   - [commercial-balance-ui.md](commercial-balance-ui.md)

5) **Notificacoes (CP + Admin UI)**
   - [notifications.md](notifications.md)
   - [notifications-ui.md](notifications-ui.md)

6) **Relacionamentos (criar/editar)**
   - [relationships-create-edit-ui.md](relationships-create-edit-ui.md)

7) **Aprovacoes de trade (UI)**
   - [trades-approval-ui.md](trades-approval-ui.md)

## Cenarios de export_policy

Use os cenarios abaixo apos `reset-environment.md`:

- [export-policy-scenario-no-exportable.md](export-policy-scenario-no-exportable.md)
- [export-policy-scenario-single-category.md](export-policy-scenario-single-category.md)
- [export-policy-scenario-exclude-category.md](export-policy-scenario-exclude-category.md)
- [export-policy-scenario-entity-allowlist.md](export-policy-scenario-entity-allowlist.md)
- [export-policy-scenario-entity-blocklist.md](export-policy-scenario-entity-blocklist.md)

## Contribuicao rapida

- Crie runbooks novos seguindo a estrutura: Objetivo, Pre-requisitos, Passos, Validacao, Observacoes.
- Mantenha comandos com caminhos absolutos quando dependerem do ambiente local.
- Atualize este README quando adicionar, renomear ou remover runbooks.

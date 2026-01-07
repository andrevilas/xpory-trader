# Runbook: Relacionamentos (criar/editar) - UI

Data base: 2026-01-06 (BRT)

## Objetivo
Validar o fluxo de criacao/edicao de relacionamento no Admin UI, incluindo listas de categorias/entidades com contagem de ofertas ativas.

## Pre-requisitos
- CP + Admin UI + Traefik ativos (`staging-environment-setup.md`).
- WLs exporter/importer ativos e sincronizados.
- Snapshot de categorias/entidades do exporter sincronizado no CP.
- Admin UI atualizado para nao listar relacionamentos orfaos.

## /etc/hosts (staging local)
```
127.0.0.1 cp.localhost cp-jwks.localhost wl-importer.localhost wl-exporter.localhost
```

## Credenciais
- `master@xpory.local` / `changeit`

## Passos (Playwright manual)
1) Abrir `https://cp.localhost/admin/relationships` e autenticar.
2) Clicar em **Novo** (ou editar um relacionamento existente).
3) Selecionar `WL origem` e `WL destino` (exporter/importer).
4) Abrir seções de **Categorias** e **Entidades**:
   - Verificar que cada item mostra a contagem de ofertas ativas no final do item.
   - Confirmar que o campo de busca filtra e mantém a contagem visível.
5) Salvar o relacionamento com alguma policy (por exemplo, excluir categoria 15).
6) Validar que a lista volta com 2 relacionamentos e sem IDs orfaos.

## Validacao
- Lista de categorias exibe contagem (ex.: `Alimentacao ... 61`).
- Lista de entidades exibe contagem (ex.: `A3Negocios ... 3`).
- Relacionamento salvo com sucesso (toast/alerta de sucesso).
- Lista de relacionamentos exibe **2 itens** (sem orfaos).

## Validado em 2026-01-07
- Categorias exibem contagem de ofertas ativas ao final do item.
- Lista de relacionamentos mostra somente 2 itens (sem orfaos).

## Troubleshooting
- Se a contagem de categorias nao aparece, revisar `activeCount` no payload.
- Se aparecerem 4 relacionamentos, validar se o endpoint `reports/trade-balance` esta filtrando orfaos.

## Relacionados
- `reset-environment.md`
- `staging-environment-setup.md`

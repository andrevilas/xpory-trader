# Runbook: Subir ambiente staging (CP + WL + Admin UI)

## Objetivo
Documentar o fluxo de subida do ambiente staging local com todos os projetos (Control Plane, WLs e Admin UI), incluindo configuracao, deploy e validacoes minimas.

## Escopo
- xpory-trader (Control Plane - CP)
- xpory-core (WLs exporter/importer)
- xpory-trader-admin-ui (Admin UI)
- Banco de dados e proxy/reverso (quando aplicavel)

## Pre-requisitos
- Acesso aos repositorios locais.
- Docker + Docker Compose.
- Certificados mTLS disponiveis no repo do CP.

## /etc/hosts (staging local)
```
127.0.0.1 cp.localhost cp-jwks.localhost wl-importer.localhost wl-exporter.localhost
```

## Variaveis e segredos (exemplos)
- CP:
  - `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
  - `SERVER_SSL_*` (keystore/truststore)
  - `JWT_SIGNING_KEY`, `ADMIN_USERS_JWT_SECRET`
  - `ADMIN_USERS_*` (usuarios bootstrap)
- WLs (xpory-core):
  - URLs do CP, tokens de integracao, flags de integracao externa
- Admin UI:
  - `VITE_CP_BASE_URL` (ex.: https://<cp-host>/admin/api)
  - `VITE_BASE_PATH` (ex.: /admin/)

## Artefatos (locais)
- CP: imagem local `xpory-trader` (build via `docker compose`).
- Admin UI: imagem local `xpory-trader-admin-ui` (build via `docker compose`).
- WLs: processos locais do repo `xpory-core` (fora deste repo).

## Endpoints esperados (local)
- CP (via Traefik): `https://cp.localhost`
- Admin UI: `https://cp.localhost/admin/`
- WL Exporter: `http://localhost:8081`
- WL Importer: `http://localhost:8090`

## Passos

### 1) Preparar versoes
1. Fixar a versao de cada projeto (tag ou commit aprovado).
2. Garantir que o CP e a Admin UI sao buildados localmente via `docker compose`.
3. Garantir que o xpory-core esta com as configuracoes locais necessarias (porta, mTLS, CP).

### 2) Subir o Control Plane (CP)
1. Subir CP + Postgres + Admin UI:

```
cd /home/andre/Trabalho/XporY/repos/github/xpory-trader

docker compose up -d
```

2. Subir o Traefik (proxy TLS + mTLS para o CP):

```
docker compose -f docker-compose.proxy.yml up -d
```

3. Validar logs iniciais e conexao com o banco.

### 2.1) Confiar na CA (browser)
- Para evitar aviso de HTTPS, importe `traefik/certs/ca.crt` como CA confiavel no sistema operacional ou no navegador.

### 3) Subir as WLs (xpory-core)
1. Subir WL exporter e importer no repo `xpory-core` usando os scripts oficiais:

```
cd /home/andre/Trabalho/XporY/repos/xpory-core

./scripts/run-exporter.sh
```

```
cd /home/andre/Trabalho/XporY/repos/xpory-core

./scripts/run-importer.sh
```

2. Validar conexao ao CP e sincronizacao inicial.
3. Garantir que os gateways/rotas estao acessiveis pelo CP (ex.: `host.docker.internal`).
4. No importer, garantir `APP_CP_EXPORTERS_JSON_OVERRIDE` usando o exporter com `?importerWlId=<IMPORTER_WL_ID>`:
   - Ex.: `[{\"id\":\"<EXPORTER_WL_ID>\",\"name\":\"wl-exporter\",\"baseUrl\":\"http://localhost:8081\",\"path\":\"/api/v2/control-plane/export/offers?importerWlId=<IMPORTER_WL_ID>\"}]`

### 4) Subir Admin UI
1. A UI sobe junto com o `docker compose` do CP (build local).
2. Validar acesso ao endpoint `/admin/`.

### 5) Validacao minima
- Login no Admin UI.
- Listagem de WLs e relacionamentos no CP.
- Sincronizacao de ofertas entre exporter -> importer.
- Confirmar que notificacoes e endpoints principais respondem.

## Validacao
- CP responde via Traefik em `https://cp.localhost/admin/api/health` (ou endpoint equivalente).
- Admin UI carrega e autentica em `https://cp.localhost/admin/`.
- WLs aparecem no Admin UI e sincronizam ofertas.

## Troubleshooting
- CP nao sobe: validar conexao do banco e variaveis `SERVER_SSL_*` no `docker-compose.yml`.
- Admin UI sem API: validar `VITE_CP_BASE_URL` e proxy reverso (Traefik).
- WLs sem sync: validar URLs e tokens de integracao com CP.

## Validado em 2026-01-07
- CP + WLs + Admin UI sobem e autenticam via Traefik/mTLS.
- Rotas principais e sincronizacao inicial funcionam.

## Observacoes
- Este staging e local e usa mTLS via Traefik.
- Use `admin-ui.md` para detalhes de certificados e trafego.

## Relacionados
- `reset-environment.md`
- `admin-ui.md`
- `notifications.md`

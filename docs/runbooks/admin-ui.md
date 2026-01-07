# Runbook: XPORY Trader Admin UI (Traefik mTLS)

## Objetivo
Executar a Admin UI usando o stack local do Control Plane com Traefik como proxy e cliente mTLS. O browser fala com o Traefik via HTTPS e o Traefik autentica no Control Plane com certificado cliente. **Nao usamos o admin-bff** neste fluxo.

## Pre-requisitos
- Docker + Docker Compose
- `openssl` e `keytool` (para validar certificados quando necessario)

## /etc/hosts (staging local)
```
127.0.0.1 cp.localhost cp-jwks.localhost wl-importer.localhost wl-exporter.localhost
```

## Certificados
Este repositorio ja contem os artefatos necessarios:

- Control Plane (servidor TLS): `tls/server/server.p12` (senha `changeit`)
- CA (truststore): `tls/ca/ca-truststore.p12` (senha `changeit`)
- CA (PEM): `tls/ca/ca.crt`
- Certificado cliente para o Traefik: `tls/client/client.crt` + `tls/client/client.key`

Para evitar aviso de HTTPS no browser, importe `traefik/certs/ca.crt` como CA confiavel no sistema operacional ou no navegador.

Nota: o certificado do Control Plane local e emitido para `localhost`. O Traefik valida esse nome via `serverName: localhost` no `traefik/dynamic.yml`.

## Passos

### 1) Subir o stack
1) Suba o Control Plane + Postgres + Admin UI:

```
cd /home/andre/Trabalho/XporY/repos/github/xpory-trader

docker compose up -d
```

2) Suba o Traefik (proxy TLS + mTLS para o CP):

```
docker compose -f docker-compose.proxy.yml up -d
```

3) Acesse a Admin UI:

```
https://cp.localhost/admin/
```

### 2) Como funciona o trafego
- Browser -> Traefik: HTTPS (certificado `cp.localhost` em `traefik/certs`).
- Traefik -> Control Plane: HTTPS com mTLS usando `tls/client/client.crt` e `tls/client/client.key`.
- Admin UI -> API: `VITE_CP_BASE_URL=/admin/api` (mesmo host). O Traefik remove o prefixo `/admin/api` e encaminha para o CP.

### 3) Autenticacao e usuarios
- A Admin UI exige login interno (JWT) alem do mTLS.
- Fluxo completo: Browser -> Traefik (TLS) -> CP (mTLS) -> UI faz login em `/auth/login`.
- O CP cria usuarios padrao no bootstrap caso nao existam:
  - MASTER: `ADMIN_USERS_MASTER_EMAIL` / `ADMIN_USERS_MASTER_PASSWORD` (default `master@xpory.local` / `changeit`)
  - TRADER: `ADMIN_USERS_TRADER_EMAIL` / `ADMIN_USERS_TRADER_PASSWORD` (default `trader@xpory.local` / `changeit`)
- A pagina **Usuarios** (apenas MASTER) gerencia novos usuarios e reset de senha.
- A pagina **Aprovacoes** lista trades pendentes e permite aprovar/rejeitar.

## Validacao
- Admin UI abre em `https://cp.localhost/admin/`.
- Login com usuario MASTER ou TRADER funciona.
- Requisicoes para `/admin/api` respondem sem erro TLS.

## Troubleshooting
- UI carregando mas API falhando: confirme o CP esta respondendo em `https://host.docker.internal:8080` com mTLS.
- Erro TLS no CP: confirme `SERVER_SSL_*` no `docker-compose.yml` e a senha `changeit` nos keystores.
- Erro 404 em assets: confirme que a UI foi buildada com `VITE_BASE_PATH=/admin/`.
- Banner "Invalid token" nas telas: sair e autenticar novamente (JWT expirado/antigo).

## Parar o stack
```
docker compose -f docker-compose.proxy.yml down

docker compose down
```

## Validado em 2026-01-07
- Admin UI acessa e autentica via mTLS + JWT.
- Navegacao e chamadas `/admin/api` respondem sem erros.

## Relacionados
- `staging-environment-setup.md`
- `notifications.md`

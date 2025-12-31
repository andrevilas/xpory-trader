# Peer Token

O endpoint `POST /wls/{importerId}/peer-token` emite tokens de curta duracao
usados na autenticacao entre WLs (importacao de ofertas e compra cross-WL).

## Request

```json
{
  "targetWlId": "wl-exporter",
  "scopes": ["offers:sync", "trader:purchase"]
}
```

## Response

```json
{
  "token": "<jwt>",
  "expiresInSeconds": 300
}
```

## Regras

- `importerId` e `targetWlId` devem existir e estar ativos.
- Relationship ativo deve existir entre `targetWlId -> importerId`.
- Scopes aceitos: `offers:sync`, `trader:purchase`.

## Telemetria

- Evento: `PEER_TOKEN_ISSUED`
- Campos: `targetWlId`, `scopes`

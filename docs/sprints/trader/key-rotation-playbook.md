# JWT Key Rotation Playbook

The control plane manages per-WL RSA key pairs in the `cp_white_label_keys`
table. Rotate keys without downtime using the public API and Liquibase-managed
storage.

## Manual rotation for a tenant

```bash
curl -k --cert tls/wl-a/wl_a.p12:changeit --cert-type P12 \
  -X POST https://localhost:8443/cpservice/wls/<WL_ID>/keys/rotate
```

The response returns the new `kid`, which is also published via
`/.well-known/jwks.json`. Distribute the updated `kid` to downstream
validators if they cache keys between pulls.

## Bulk rotation

Wire an automation (cron/CI) to iterate the WL catalog and call the endpoint.
For example:

```bash
for wl in wl-a wl-b; do
  curl -k --cert tls/${wl}/${wl}.p12:changeit --cert-type P12 \
    -X POST "https://localhost:8443/cpservice/wls/${wl}/keys/rotate"
done
```

Because tokens are short-lived (≤5 minutes), clients will automatically pick up
the new key after the next issuance. Keep the previous key marked inactive but
retained in the table for audit purposes.

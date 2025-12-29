# Control Plane mTLS Layout

This repository now ships ready-to-use keystore material for the white-label
apps under `tls/`. Assets are sourced from the `xpory-pki` project and mounted
into the WL containers to satisfy the `APP_CP_MTLS_*` environment variables.

## Directory structure

```
tls/
  wl-a/
    wl_a.p12        # client key/cert bundle (alias: wl_a)
    ca-trust.p12    # trust store containing XPORY Root CA (alias: xpory-root)
  wl-b/
    wl_b.p12        # client key/cert bundle (alias: wl_b)
    ca-trust.p12
```

Both keystores use the default passphrase `changeit`. Adjust `APP_CP_MTLS_*`
variables in the env files if you rotate passwords or generate fresh bundles.
[control-plane-mtls.md](control-plane-mtls.md)
## Refreshing certificates

1. Issue new client certificates with the PKI helper, exporting PKCS#12
   bundles:

   ```bash
   cd xpory-pki
   PKCS12_PASSWORD=changeit bin/issue-client.sh wl_a --cn wl_a --dns xpory-wl-a.local
   PKCS12_PASSWORD=changeit bin/issue-client.sh wl_b --cn wl_b --dns xpory-wl-b.local
   ```

2. Replace `tls/wl-a/wl_a.p12`, `tls/wl-b/wl_b.p12` with the generated files.
3. Rebuild or restart the dependent containers (`docker compose up -d xpory-wl-a`) so
   Grails reloads the keystore configuration.

For trust stores, re-run:

```bash
openssl pkcs12 -export -nokeys -in xpory-pki/certs/ca.crt.pem \
  -out tls/wl-a/ca-trust.p12 -name xpory-root -passout pass:changeit
```

(Repeat for `wl-b`).

## Environment variables

`env/wl-a.env` and `env/wl-b.env` now define:

- `APP_CP_MTLS_KEY_STORE`, `APP_CP_MTLS_KEY_STORE_PASSWORD`,
  `APP_CP_MTLS_KEY_STORE_TYPE`, `APP_CP_MTLS_KEY_PASSWORD`, `APP_CP_MTLS_KEY_ALIAS`
  – used by the WL app to load its client certificate.
- `APP_CP_MTLS_TRUST_STORE`, `APP_CP_MTLS_TRUST_STORE_PASSWORD`,
  `APP_CP_MTLS_TRUST_STORE_TYPE` – point at the trust bundle with the XPORY
  Root CA.

These variables pair with the volume mounts declared in `docker-compose.yml` so
the WL containers see `/etc/xpory/tls` populated with the proper keystores.

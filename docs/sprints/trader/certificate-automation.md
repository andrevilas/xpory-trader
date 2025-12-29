# Certificate Automation

This repository now keeps every certificate input under version control so we
can recreate the TLS assets for the control-plane gateway and each white label
(WL) with a single command. The workflow lives under `xpory-pki/` and builds on
the existing CA scripts.

## Directory layout

| Path | Purpose |
|------|---------|
| `xpory-pki/provisioning/server.env` | Declares how the gateway/control-plane server certificate should be issued and where the PEM files must be copied. |
| `xpory-pki/provisioning/wls.manifest` | Line-delimited manifest, one WL per row (format described below). Drives PKCS#12 and trust-store generation. |
| `xpory-pki/bin/rotate-certs.sh` | Entry point that issues/rotates the server certificate plus every WL bundle defined in the manifest. |
| `tls/<wl-id>/` | Host volume mounted by each WL docker-compose stack. Receives the keystore + trust-store created during rotation. |
| `nginx/certs/` | Gateway volume that receives the TLS server certificate and client CA bundle. |

## WL manifest format

Each non-empty line in `xpory-pki/provisioning/wls.manifest` must follow:

```
name|cn|dns-san-list|pkcs12_password|keystore_path|truststore_path|truststore_password
```

* `name` – Logical identifier; also used for the PEM/PKCS12 filenames stored in
  `xpory-pki/certs/` and `xpory-pki/private/`.
* `cn` – Subject Common Name (defaults to `name` if left blank).
* `dns-san-list` – Comma-separated list of DNS entries to embed in the
  certificate SAN. Leave empty for none.
* `pkcs12_password` – Protects the generated PKCS#12 keystore.
* `keystore_path` – Where the PKCS#12 bundle should be copied (path relative to
  `xpory-pki/`). Point this at the directory mounted by the WL compose stack
  (e.g. `../tls/wl-a/wl_a.p12`).
* `truststore_path` & `truststore_password` – Location and password for the
  WL-facing trust-store that only contains the XPORY root CA. This is what
  `APP_CP_MTLS_TRUST_STORE` points to inside each WL container.

Add one line per WL so that rotations remain deterministic under git.

## Server configuration

`xpory-pki/provisioning/server.env` overrides the default server settings used
by `rotate-certs.sh` (CN, SANs, file targets). Adjust the SAN list or output
paths there whenever the gateway stack changes. Because the env file lives in
source control we can review and promote changes alongside code.

## Running rotations

1. Ensure the root CA exists once per environment:
   ```bash
   cd xpory-pki
   bin/ca-init.sh   # only if certs/ca.crt.pem is missing
   ```
2. Rotate everything (gateway + all WLs):
   ```bash
   bin/rotate-certs.sh
   ```
3. Rotate a single WL after updating the manifest line:
   ```bash
   bin/rotate-certs.sh --clients-only --wl wl_d
   ```
4. Push the updated `tls/<wl-id>` secrets to the target host (or secret store)
   and restart only the affected docker-compose stack(s).

Flags worth knowing:

- `--server-only` – reissues the gateway cert without touching WL bundles.
- `--clients-only` / `--skip-server` – updates WL bundles only (handy when
  onboarding a new tenant).
- `--manifest <path>` and `--server-config <path>` – let CI pipelines point to
  alternate config files if needed.

## Adding a new WL stack

1. Append a new line to `xpory-pki/provisioning/wls.manifest` with the WL id,
   SANs, passwords, and keystore/trust-store targets.
2. Commit the change so reviewers can validate the inputs (passwords should be
   development/demo values only; production secrets belong in your secrets
   manager and can be injected at runtime via environment overrides).
3. Run `bin/rotate-certs.sh --clients-only --wl <name>` to mint the new bundle.
4. Mount the resulting directory (`tls/<wl-id>`) inside the WL docker compose
   file and wire the env vars (`APP_CP_MTLS_KEY_STORE`, `APP_CP_MTLS_TRUST_STORE`).
5. Deploy/restart the WL stack. Because the certificate artifacts are produced
   deterministically you can always regenerate them later by re-running the same
   command.

## Operational tips

- Keep the provisioning files (`server.env` + `wls.manifest`) short and audited
  with code review—changes to SANs or passwords will surface in git diffs.
- Use CI to run `bin/rotate-certs.sh --server-only` or `--clients-only` on a
  60–90 day schedule. Commit the refreshed artifacts (or publish them to your
  secure bucket) and trigger a deployment job that restarts the affected
  containers.
- Never commit the contents of `xpory-pki/private/` or the exported PKCS#12
  files; only the provisioning metadata belongs in git. The rotation script
  copies artifacts into `tls/` and `nginx/certs/`, which stay out of the repo
  and are distributed through your deployment channel.

With this flow every environment shares the same, repeatable certificate
playbook—no more ad-hoc openssl invocations or manual copy/paste mistakes.

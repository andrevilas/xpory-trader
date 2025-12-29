# Certificate & JWT Playbook

This guide documents the baseline operational steps required to keep the Wave 0 control-plane compliant with the security objectives.

## 1. mTLS Topology

```
WL Node ── mTLS ──> Control Plane (Tomcat)
             ▲
             │ corporate CA (XporY Root + issuing intermediates)
```

### Server configuration

Set the following environment variables before starting the CP service (or inject via Kubernetes Secrets):

| Variable | Description |
|----------|-------------|
| `SERVER_SSL_ENABLED=true` | Turns on the HTTPS listener. |
| `SERVER_SSL_KEY_STORE` | Path to the server key-store (PKCS12 recommended). |
| `SERVER_SSL_KEY_STORE_PASSWORD` | Passphrase for the key-store. |
| `SERVER_SSL_TRUST_STORE` | Trust-store that contains the **issuing** corporate CAs. |
| `SERVER_SSL_TRUST_STORE_PASSWORD` | Passphrase for the trust-store. |
| `SERVER_SSL_CLIENT_AUTH=need` | Forces Tomcat to request & validate client certificates. |

Key-store generation example (convert PEM bundle to PKCS12):

```bash
openssl pkcs12 -export \
  -in cpserver.crt \
  -inkey cpserver.key \
  -certfile corporate-ca-chain.pem \
  -name cpservice \
  -out cpservice.p12
```

### Client trust validation

- WL nodes must present certificates issued by the XporY corporate CA hierarchy.
- Certificates must include the WL identifier in the SAN (e.g. `URI:wl://{wlId}`) – enforced by corporate PKI policy.
- Revocation checks are handled upstream (OCSP/CRL). Upstream ingress should terminate sessions if certificates are revoked.

## 2. JWT Issuance & Validation (JWKS / RS256)

### Signing material

`JwtService` issues **RS256** tokens using per-WL RSA key pairs stored in
`cp_white_label_keys`. Each token is signed with the active key for the WL and
includes a `kid` header. Public keys are exposed via `/.well-known/jwks.json`.

### Token characteristics

- TTL capped at 300 seconds (`JWT_TTL_SECONDS` override allowed for shorter values).
- Claims include `sub` (WL id), `wlId`, `scopes` array, `iss` (`xpory-control-plane`), `aud` (WL id).
- `kid` header is required and must match an active key in JWKS.
- `JwtService.decode(token)` is available for resource servers that co-locate with the CP.

### Validation checklist for consumers

- Verify signature with the JWKS public key that matches `kid`.
- Ensure `exp` is in the future and not more than 5 minutes away from `iat`.
- Restrict behaviour to the scopes in the token payload.

## 3. Rotation workflow (≤ 90 days)

| Asset | Rotation trigger | Automation hook |
|-------|------------------|-----------------|
| TLS server certificate | Corporate CA pushes new cert bundle | Replace files referenced by `SERVER_SSL_KEY_STORE` & `SERVER_SSL_TRUST_STORE` and restart pods/containers. |
| JWT signing key | WL key expires or manual rotation | Call `POST /wls/{id}/keys/rotate` (per WL) or bulk rotate; JWKS updates immediately. |

The `CertificateRotationService` schedules a refresh job:

- Initial check 5 minutes after boot.
- Interval = `CERT_ROTATION_DAYS` (default 90). Override for emergency rotations by setting it lower and restarting.

### Manual rotate JWT key without restart

1. Rotate for a tenant:
   ```bash
   curl -k --cert tls/wl-a/wl_a.p12:changeit --cert-type P12 \
     -X POST https://cp.localhost/wls/<WL_ID>/keys/rotate
   ```
2. Validate new tokens by issuing `POST /wls/{id}/token` and decoding with JWKS.

### Validating successful TLS rotation

- Use `openssl s_client -connect cp.example.com:8443 -showcerts` to read the served certificate chain.
- Confirm the presented certificate serial matches the latest issued by corporate CA.
- Check application logs for `Scheduled certificate and key rotation...` entries and absence of TLS handshake errors.

## 4. Incident response

- In case of compromised JWT key, rotate the affected WL key immediately and revoke the old key (set inactive). Optionally reduce `JWT_TTL_SECONDS` temporarily (e.g. 60) to shorten exposure.
- For revoked WL client certificates, update the CRL/OCSP upstream. Additionally, flag the WL as `inactive` via `POST /wls` update (future API) to stop policy exports.

## 5. Future enhancements

- Integrate with a corporate secrets manager instead of file/env secrets.
- Emit rotation events into the telemetry stream for auditability.
- Expose readiness probe that validates trust-store currency.

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CERT_DIR="${ROOT_DIR}/traefik/certs"

mkdir -p "${CERT_DIR}"

CA_KEY="${CERT_DIR}/ca.key"
CA_CRT="${CERT_DIR}/ca.crt"

if [[ ! -f "${CA_KEY}" || ! -f "${CA_CRT}" ]]; then
  openssl req -x509 -newkey rsa:2048 -nodes \
    -keyout "${CA_KEY}" \
    -out "${CA_CRT}" \
    -days 365 \
    -subj "/CN=XPORY-Local-CA"
fi

make_cert() {
  local name="$1"
  local cn="$2"
  local cert="${CERT_DIR}/${name}.crt"
  local key="${CERT_DIR}/${name}.key"
  local csr="${CERT_DIR}/${name}.csr"
  local ext="${CERT_DIR}/${name}.ext"

  if [[ -f "${cert}" && -f "${key}" ]]; then
    return
  fi

  openssl req -newkey rsa:2048 -nodes \
    -keyout "${key}" \
    -out "${csr}" \
    -subj "/CN=${cn}"

  printf "subjectAltName=DNS:%s" "${cn}" > "${ext}"

  openssl x509 -req \
    -in "${csr}" \
    -CA "${CA_CRT}" \
    -CAkey "${CA_KEY}" \
    -CAcreateserial \
    -out "${cert}" \
    -days 365 \
    -extfile "${ext}"
}

make_cert "cp.localhost" "cp.localhost"
make_cert "cp-jwks.localhost" "cp-jwks.localhost"
make_cert "wl-importer.localhost" "wl-importer.localhost"
make_cert "wl-exporter.localhost" "wl-exporter.localhost"

# Client cert for WL -> CP mTLS
CLIENT_KEY="${CERT_DIR}/wl-client.key"
CLIENT_CSR="${CERT_DIR}/wl-client.csr"
CLIENT_CRT="${CERT_DIR}/wl-client.crt"

if [[ ! -f "${CLIENT_CRT}" || ! -f "${CLIENT_KEY}" ]]; then
  openssl req -newkey rsa:2048 -nodes \
    -keyout "${CLIENT_KEY}" \
    -out "${CLIENT_CSR}" \
    -subj "/CN=wl-client"

  openssl x509 -req \
    -in "${CLIENT_CSR}" \
    -CA "${CA_CRT}" \
    -CAkey "${CA_KEY}" \
    -CAcreateserial \
    -out "${CLIENT_CRT}" \
    -days 365
fi

# PKCS12 bundle for WL client
openssl pkcs12 -export \
  -in "${CLIENT_CRT}" \
  -inkey "${CLIENT_KEY}" \
  -certfile "${CA_CRT}" \
  -out "${CERT_DIR}/wl-client.p12" \
  -name wl-client \
  -password pass:changeit

# Truststore for WL to trust CP certs (idempotent)
if keytool -list -keystore "${CERT_DIR}/ca-truststore.p12" -storetype PKCS12 -storepass changeit -alias xpory-local-ca >/dev/null 2>&1; then
  keytool -delete \
    -alias xpory-local-ca \
    -keystore "${CERT_DIR}/ca-truststore.p12" \
    -storetype PKCS12 \
    -storepass changeit
fi

keytool -importcert -noprompt \
  -alias xpory-local-ca \
  -file "${CA_CRT}" \
  -keystore "${CERT_DIR}/ca-truststore.p12" \
  -storetype PKCS12 \
  -storepass changeit

echo "Certificates generated under ${CERT_DIR}"

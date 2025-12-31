# Sprint: peer-token

## Objetivo
Implementar no xpory-trader o endpoint de peer token do Control Plane e os
componentes necessarios para emissao/validacao, observabilidade e testes.

## Escopo
- Endpoint: POST /wls/{importerId}/peer-token
- Emissao de token para autenticacao entre WLs (scopes: offers:sync, trader:purchase)
- Validacoes de seguranca e registros de telemetria
- Testes unitarios e de integracao
- Documentacao do fluxo

## Checklist de atividades

### T1 - Endpoint e contrato
- [ ] Definir request/response DTOs do peer token.
- [ ] Implementar controller do endpoint /wls/{importerId}/peer-token.
- [ ] Validar parametros obrigatorios (importerId, targetWlId, scopes).
- [ ] Garantir compatibilidade com mTLS e autenticao existente do CP.

### T2 - Emissao de token
- [ ] Implementar servico de emissao (Issuer) com JWT assinado.
- [ ] Incluir claims: sub (targetWlId), aud (targetWlId), scopes, wlId (importerId).
- [ ] TTL curto (ex: 5 min) e configuravel.
- [ ] Reusar/estender JWKS existente para assinaturas.

### T3 - Validacoes de negocio
- [ ] Validar se o importerId existe e esta ativo.
- [ ] Validar se o targetWlId existe e esta ativo.
- [ ] Validar scopes permitidos e rejeitar desconhecidos.
- [ ] Verificar se o importer possui relacionamento/visibilidade com target.

### T4 - Observabilidade e telemetria
- [ ] Log estruturado de emissao (wlId, targetWlId, scopes, ttl).
- [ ] Telemetria CP: evento PEER_TOKEN_ISSUED com metadata.
- [ ] Erros relevantes (403, 404, 422) com detalhes de validacao.

### T5 - Testes
- [ ] Unit tests do service de emissao de token.
- [ ] Unit tests do controller (validacao de payload e respostas).
- [ ] Integration test completo para /wls/{id}/peer-token.
- [ ] Casos negativos: wl inexistente, scope invalido, sem relacao.

### T6 - Documentacao
- [ ] Atualizar docs/sprints/trader/control-plane-api.md com endpoint.
- [ ] Registrar novo fluxo em docs/sprints/trader/peer-token/README.md.
- [ ] Adicionar exemplos de request/response.

## Dependencias
- Chaves de assinatura JWKS/keystore do CP
- Regras de visibilidade/relacionamento entre WLs

## Saidas esperadas
- Endpoint funcional com autenticacao e autorizacao
- Token valido para uso nos WLs importadores
- Testes passando em pipeline
- Documentacao atualizada

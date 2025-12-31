package cpservice

import grails.core.GrailsApplication
import grails.gorm.transactions.Transactional
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwsHeader
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.SignatureException
import io.jsonwebtoken.SigningKeyResolverAdapter

import javax.annotation.PostConstruct
import java.security.Key
import java.security.PrivateKey
import java.security.PublicKey
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * Issues short-lived JWTs for WL nodes.
 */
@Transactional
class JwtService {

    GrailsApplication grailsApplication
    JwtKeyService jwtKeyService

    int tokenTtlSeconds = 300
    int peerTokenTtlSeconds = 300
    private SigningKeyResolverAdapter signingKeyResolver

    @PostConstruct
    void init() {
        tokenTtlSeconds = (grailsApplication.config.security.jwt.ttlSeconds ?: 300) as int
        peerTokenTtlSeconds = (grailsApplication.config.security.jwt.peerTokenTtlSeconds ?: tokenTtlSeconds ?: 300) as int
        signingKeyResolver = new WhiteLabelSigningKeyResolver(jwtKeyService)
    }

    String issueToken(String whiteLabelId, Collection<String> scopes) {
        if (!whiteLabelId) {
            throw new IllegalArgumentException('whiteLabelId is required for token issuance')
        }

        WhiteLabelSigningKey keyEntry = jwtKeyService.ensureActiveKey(whiteLabelId)
        PrivateKey signingKey = (PrivateKey) jwtKeyService.toPrivateKey(keyEntry)

        Instant now = Instant.now()
        Instant expiry = now.plusSeconds(Math.min(tokenTtlSeconds, 300))

        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setIssuer(grailsApplication.config.security.jwt.issuer ?: 'xpory-control-plane')
                .setSubject(whiteLabelId)
                .setAudience(whiteLabelId)
                .claim('scopes', (scopes ?: ['policies:read']).unique())
                .claim('wlId', whiteLabelId)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .setHeaderParam('kid', keyEntry.keyId)
                .signWith(SignatureAlgorithm.RS256, signingKey)
                .compact()
    }

    String issuePeerToken(String importerId, String targetWlId, Collection<String> scopes) {
        if (!importerId || !targetWlId) {
            throw new IllegalArgumentException('importerId and targetWlId are required')
        }

        WhiteLabelSigningKey keyEntry = jwtKeyService.ensureActiveKey(importerId)
        PrivateKey signingKey = (PrivateKey) jwtKeyService.toPrivateKey(keyEntry)

        Instant now = Instant.now()
        Instant expiry = now.plusSeconds(Math.min(peerTokenTtlSeconds, 300))

        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setIssuer(grailsApplication.config.security.jwt.issuer ?: 'xpory-control-plane')
                .setSubject(targetWlId)
                .setAudience(targetWlId)
                .claim('scopes', (scopes ?: []).unique())
                .claim('wlId', importerId)
                .claim('targetWlId', targetWlId)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .setHeaderParam('kid', keyEntry.keyId)
                .signWith(SignatureAlgorithm.RS256, signingKey)
                .compact()
    }

    void refreshSigningMaterial() {
        signingKeyResolver = new WhiteLabelSigningKeyResolver(jwtKeyService)
    }

    Map<String, Object> decode(String token) {
        def body = Jwts.parser()
                .setSigningKeyResolver(signingKeyResolver)
                .parseClaimsJws(token)
                .body
        return body as Map<String, Object>
    }

    private static class WhiteLabelSigningKeyResolver extends SigningKeyResolverAdapter {
        private final JwtKeyService jwtKeyService

        WhiteLabelSigningKeyResolver(JwtKeyService jwtKeyService) {
            this.jwtKeyService = jwtKeyService
        }

        @Override
        Key resolveSigningKey(JwsHeader header, Claims claims) {
            String kid = header?.keyId
            WhiteLabelSigningKey entry = jwtKeyService.findByKeyId(kid)
            if (!entry || !entry.active) {
                throw new SignatureException("Unknown or inactive key id ${kid}")
            }
            PublicKey publicKey = jwtKeyService.toPublicKey(entry)
            return publicKey
        }
    }
}

package cpservice

import grails.core.GrailsApplication
import grails.gorm.transactions.Transactional
import grails.util.Holders
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date
import java.util.UUID

@Transactional
class AdminAuthService {

    GrailsApplication grailsApplication
    String jwtSecretOverride
    Integer jwtTtlSecondsOverride

    String issueToken(AdminUser user) {
        if (!user) {
            throw new IllegalArgumentException('user is required')
        }
        String secret = resolveSecret()
        int ttlSeconds = resolveTtlSeconds()
        Instant now = Instant.now()
        Instant expiry = now.plusSeconds(ttlSeconds)
        Date expiresAt = Date.from(expiry)
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(user.id?.toString())
                .claim('userId', user.id?.toString())
                .claim('email', user.email)
                .claim('role', user.role)
                .setIssuedAt(Date.from(now))
                .setExpiration(expiresAt)
                .claim('expiresAt', expiresAt)
                .signWith(SignatureAlgorithm.HS256, secret.getBytes(StandardCharsets.UTF_8))
                .compact()
    }

    Map<String, Object> parseToken(String token) {
        if (!token) {
            return null
        }
        String secret = resolveSecret()
        Claims claims = Jwts.parser()
                .setSigningKey(secret.getBytes(StandardCharsets.UTF_8))
                .parseClaimsJws(token)
                .body
        def expiresAt = claims.expiration ?: claims.get('expiresAt')
        def userId = claims.get('userId') ?: claims.subject
        return [
                userId: userId?.toString(),
                email : claims.get('email')?.toString(),
                role  : claims.get('role')?.toString(),
                expiresAt: expiresAt
        ]
    }

    private String resolveSecret() {
        if (jwtSecretOverride) {
            return jwtSecretOverride
        }
        def config = grailsApplication?.config ?: Holders.config
        String secret = config?.getProperty('security.adminUsers.jwtSecret', String)
        if (!secret) {
            secret = config?.security?.adminUsers?.jwtSecret?.toString()
        }
        if (!secret) {
            secret = System.getProperty('security.adminUsers.jwtSecret')
        }
        if (!secret) {
            secret = System.getenv('ADMIN_USERS_JWT_SECRET')
        }
        if (!secret) {
            throw new IllegalStateException('adminUsers.jwtSecret is required')
        }
        return secret.toString()
    }

    private int resolveTtlSeconds() {
        if (jwtTtlSecondsOverride) {
            return jwtTtlSecondsOverride as int
        }
        def config = grailsApplication?.config ?: Holders.config
        Integer ttl = config?.getProperty('security.adminUsers.jwtTtlSeconds', Integer)
        if (!ttl) {
            ttl = config?.security?.adminUsers?.jwtTtlSeconds as Integer
        }
        return (ttl ?: 3600) as int
    }
}

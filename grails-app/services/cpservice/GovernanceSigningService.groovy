package cpservice

import grails.core.GrailsApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

import groovy.json.JsonOutput

class GovernanceSigningService {

    private static final Logger LOG = LoggerFactory.getLogger(GovernanceSigningService)
    private static final String HMAC_ALGORITHM = 'HmacSHA256'

    GrailsApplication grailsApplication

    Map signPayload(Map payload) {
        if (!payload) {
            throw new IllegalArgumentException('payload is required for signing')
        }
        Map<String, Object> canonical = canonicalize(payload)
        String json = JsonOutput.toJson(canonical)
        byte[] signatureBytes = mac().doFinal(json.getBytes('UTF-8'))
        String encoded = signatureBytes.encodeBase64().toString()
        if (LOG.isDebugEnabled()) {
            LOG.debug('Signing governance payload for control plane response')
        }
        Date issuedAt = new Date()
        [
                value     : encoded,
                algorithm : 'HMAC-SHA256',
                issuedAt  : issuedAt
        ]
    }

    private Mac mac() {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(new SecretKeySpec(secretBytes(), HMAC_ALGORITHM))
        mac
    }

    private byte[] secretBytes() {
        def secret = grailsApplication?.config?.governance?.signatureSecret ?: System.getenv('APP_CP_GOVERNANCE_SIGNING_SECRET')
        if (!secret) {
            LOG.error('Governance signing secret not configured')
            throw new IllegalStateException('Governance signing secret not configured')
        }
        secret.toString().getBytes('UTF-8')
    }

    @SuppressWarnings('unchecked')
    private Map<String, Object> canonicalize(Map source) {
        Map<String, Object> target = new LinkedHashMap<>()
        source.keySet().collect { it.toString() }.sort().each { String key ->
            def entry = source.find { it.key?.toString() == key }
            target[key] = canonicalizeValue(entry?.value)
        }
        target
    }

    private Object canonicalizeValue(Object value) {
        if (value instanceof Map) {
            return canonicalize(value as Map)
        }
        if (value instanceof Collection) {
            return (value as Collection).collect { canonicalizeValue(it) }
        }
        if (value instanceof Date) {
            return (value as Date)
                    .toInstant()
                    .truncatedTo(ChronoUnit.SECONDS)
                    .atOffset(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_INSTANT)
        }
        return value
    }
}

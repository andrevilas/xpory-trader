package cpservice

import grails.converters.JSON
import grails.core.GrailsApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus

import java.security.MessageDigest
import java.security.cert.X509Certificate

class AdminAuthInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AdminAuthInterceptor)

    int order = 10

    GrailsApplication grailsApplication

    AdminAuthInterceptor() {
        match(controller: 'whiteLabel', action: 'index')
        match(controller: 'whiteLabel', action: 'save')
        match(controller: 'whiteLabel', action: 'show')
        match(controller: 'whiteLabel', action: 'policies')
        match(controller: 'whiteLabel', action: 'policyRevisions')
        match(controller: 'whiteLabel', action: 'updatePolicies')
        match(controller: 'traderAccount', action: 'show')
        match(controller: 'traderAccount', action: 'upsert')
        match(controller: 'relationship', action: 'index')
        match(controller: 'relationship', action: 'show')
        match(controller: 'relationship', action: 'update')
        match(controller: 'imbalance', action: 'submit')
        match(controller: 'imbalance', action: 'ack')
        match(controller: 'telemetry', action: 'list')
        match(controller: 'key', action: 'rotate')
        match(controller: 'reports', action: 'tradeBalance')
    }

    boolean before() {
        if ('OPTIONS'.equalsIgnoreCase(request.method)) {
            return true
        }
        if (!isAdminAuthEnabled()) {
            return true
        }

        X509Certificate clientCert = getClientCert()
        if (!clientCert) {
            if (isHeaderAuthEnabled() && isHeaderAuthorized()) {
                return true
            }
            response.status = HttpStatus.UNAUTHORIZED.value()
            render([error: 'Client certificate required'] as JSON)
            return false
        }

        Set<String> allowedSubjects = getAllowedSubjects()
        if (!allowedSubjects.isEmpty()) {
            String subject = clientCert.subjectX500Principal?.name ?: ''
            if (!allowedSubjects.contains(subject)) {
                LOG.warn('Admin auth rejected: subject {} not in allowlist', subject)
                response.status = HttpStatus.FORBIDDEN.value()
                render([error: 'Client certificate not authorized'] as JSON)
                return false
            }
        }

        Set<String> allowedFingerprints = getAllowedFingerprints()
        if (!allowedFingerprints.isEmpty()) {
            String fingerprint = sha256Fingerprint(clientCert)
            if (!allowedFingerprints.contains(fingerprint)) {
                LOG.warn('Admin auth rejected: fingerprint {} not in allowlist', fingerprint)
                response.status = HttpStatus.FORBIDDEN.value()
                render([error: 'Client certificate not authorized'] as JSON)
                return false
            }
        }

        return true
    }

    boolean after() { true }

    void afterView() {
        // no-op
    }

    private boolean isAdminAuthEnabled() {
        def cfg = grailsApplication?.config?.security?.admin ?: [:]
        return cfg?.enabled in [true, 'true', null]
    }

    private boolean isHeaderAuthEnabled() {
        def cfg = grailsApplication?.config?.security?.admin ?: [:]
        return cfg?.headerAuthEnabled in [true, 'true']
    }

    private boolean isHeaderAuthorized() {
        String headerSubject = request.getHeader('X-SSL-Client-Subject')
        if (!headerSubject) {
            headerSubject = request.getHeader('X-Client-Subject')
        }
        if (!headerSubject) {
            headerSubject = request.getHeader('X-Client-Id')
        }
        if (!headerSubject) {
            return false
        }
        Set<String> allowedHeaderSubjects = getAllowedHeaderSubjects()
        if (allowedHeaderSubjects.isEmpty()) {
            return true
        }
        return allowedHeaderSubjects.contains(headerSubject)
    }

    private X509Certificate getClientCert() {
        def certs = request?.getAttribute('javax.servlet.request.X509Certificate')
        if (certs instanceof X509Certificate[] && certs.length > 0) {
            return certs[0]
        }
        if (certs instanceof Collection && !certs.isEmpty()) {
            return certs[0] as X509Certificate
        }
        return null
    }

    private Set<String> getAllowedSubjects() {
        def cfg = grailsApplication?.config?.security?.admin ?: [:]
        return parseCsvList(cfg?.allowedCertSubjects)
    }

    private Set<String> getAllowedFingerprints() {
        def cfg = grailsApplication?.config?.security?.admin ?: [:]
        return parseCsvList(cfg?.allowedCertFingerprints)
                .collect { normalizeFingerprint(it) }
                .findAll { it }
                .toSet()
    }

    private Set<String> getAllowedHeaderSubjects() {
        def cfg = grailsApplication?.config?.security?.admin ?: [:]
        return parseCsvList(cfg?.allowedHeaderSubjects)
    }

    private Set<String> parseCsvList(Object raw) {
        if (!raw) {
            return [] as Set<String>
        }
        return raw.toString()
                .split(',')
                .collect { it?.trim() }
                .findAll { it }
                .toSet()
    }

    private String sha256Fingerprint(X509Certificate cert) {
        MessageDigest digest = MessageDigest.getInstance('SHA-256')
        byte[] hashed = digest.digest(cert.encoded)
        return hashed.collect { String.format('%02x', it) }.join('')
    }

    private String normalizeFingerprint(String fingerprint) {
        if (!fingerprint) {
            return null
        }
        return fingerprint.replaceAll(/[^a-fA-F0-9]/, '').toLowerCase()
    }
}

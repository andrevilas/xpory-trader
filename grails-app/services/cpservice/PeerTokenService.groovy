package cpservice

import grails.gorm.transactions.Transactional

@Transactional
class PeerTokenService {

    static final List<String> ALLOWED_SCOPES = ['offers:sync', 'trader:purchase']

    JwtService jwtService
    GovernanceTelemetryService governanceTelemetryService

    Map issuePeerToken(String importerId, String targetWlId, Collection scopes) {
        if (!importerId) {
            throw new IllegalArgumentException('importerId is required')
        }
        if (!targetWlId) {
            throw new IllegalArgumentException('targetWlId is required')
        }
        WhiteLabel importer = WhiteLabel.get(importerId)
        if (!importer) {
            throw new IllegalArgumentException('importerId not found')
        }
        WhiteLabel target = WhiteLabel.get(targetWlId)
        if (!target) {
            throw new IllegalArgumentException('targetWlId not found')
        }
        if (importer.status != 'active' || target.status != 'active') {
            throw new IllegalStateException('white label is inactive')
        }

        List<String> normalizedScopes = normalizeScopes(scopes)
        if (!normalizedScopes) {
            throw new IllegalArgumentException('scopes are required')
        }
        if (normalizedScopes.any { !ALLOWED_SCOPES.contains(it) }) {
            throw new IllegalArgumentException('unsupported scope')
        }

        Relationship relationship = Relationship.findBySourceIdAndTargetId(targetWlId, importerId)
        if (!relationship || relationship.status != 'active') {
            throw new IllegalStateException('relationship not active')
        }

        String token = jwtService.issuePeerToken(importerId, targetWlId, normalizedScopes)
        governanceTelemetryService?.recordPeerTokenIssued(importerId, [
                targetWlId: targetWlId,
                scopes    : normalizedScopes
        ])
        return [
                token           : token,
                expiresInSeconds: jwtService.peerTokenTtlSeconds
        ]
    }

    private static List<String> normalizeScopes(Collection scopes) {
        if (!scopes) {
            return []
        }
        scopes.collect { it?.toString()?.trim() }
                .findAll { it }
                .unique()
                .sort()
    }
}

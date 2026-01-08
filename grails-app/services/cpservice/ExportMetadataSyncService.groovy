package cpservice

import grails.core.GrailsApplication
import groovy.json.JsonSlurper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus

import java.net.HttpURLConnection
import java.util.UUID

class ExportMetadataSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(ExportMetadataSyncService)
    private static final Logger INTEGRATION_LOG = LoggerFactory.getLogger('cpservice.integration')

    GrailsApplication grailsApplication
    JwtService jwtService
    OfferCategorySnapshotService offerCategorySnapshotService
    OfferEntitySnapshotService offerEntitySnapshotService

    Map syncCategories(String whiteLabelId, String adminId = null, String correlationId = null) {
        syncFromWhiteLabel(whiteLabelId, '/api/v2/control-plane/offer-categories', 'categories', adminId, correlationId)
    }

    Map syncEntities(String whiteLabelId, String adminId = null, String correlationId = null) {
        syncFromWhiteLabel(whiteLabelId, '/api/v2/control-plane/entities', 'entities', adminId, correlationId)
    }

    Map syncAll() {
        List<WhiteLabel> targets = WhiteLabel.withNewSession {
            WhiteLabel.findAllByStatus('active') ?: []
        }
        Map<String, Object> summary = [ok: true, total: targets?.size() ?: 0, categories: 0, entities: 0]
        targets.each { WhiteLabel wl ->
            if (!wl?.gatewayUrl) {
                return
            }
            Map cat = syncCategories(wl.id, null, null)
            Map ent = syncEntities(wl.id, null, null)
            summary.categories = (summary.categories as Integer) + (cat?.count ?: 0) as Integer
            summary.entities = (summary.entities as Integer) + (ent?.count ?: 0) as Integer
        }
        summary
    }

    private Map syncFromWhiteLabel(String whiteLabelId, String path, String mode, String adminId, String correlationId) {
        WhiteLabel whiteLabel = WhiteLabel.get(whiteLabelId)
        if (!whiteLabel) {
            return [status: HttpStatus.NOT_FOUND.value(), body: [ok: false, error: 'Unknown white label']]
        }
        if (!whiteLabel.gatewayUrl) {
            return [status: HttpStatus.BAD_REQUEST.value(), body: [ok: false, error: 'Missing gatewayUrl']]
        }

        boolean injectedCorrelation = false
        if (!correlationId) {
            correlationId = UUID.randomUUID().toString()
            MDC.put('correlationId', correlationId)
            injectedCorrelation = true
        }

        Date collectedAt = new Date()
        String endpoint = normalizeUrl(whiteLabel.gatewayUrl, path)
        int totalImported = 0
        int offset = 0
        int limit = resolveDefaultLimit()
        Integer totalCount = null

        INTEGRATION_LOG.info('CP->WL export metadata sync start wlId={} endpoint={} mode={}', whiteLabelId, endpoint, mode)
        try {
            String token = jwtService.issueToken(whiteLabel.id, [])
            while (true) {
                String pageUrl = buildPagedUrl(endpoint, limit, offset)
                HttpURLConnection connection = (HttpURLConnection) new URL(pageUrl).openConnection()
                connection.requestMethod = 'GET'
                connection.connectTimeout = (grailsApplication?.config?.controlPlane?.connectTimeoutMillis ?: 5000) as int
                connection.readTimeout = (grailsApplication?.config?.controlPlane?.readTimeoutMillis ?: 15000) as int
                connection.setRequestProperty('Accept', 'application/json')
                connection.setRequestProperty('Authorization', "Bearer ${token}")
                if (correlationId) {
                    connection.setRequestProperty('X-Correlation-Id', correlationId)
                }

                int status = connection.responseCode
                String responseText = status < 400 ? connection.inputStream?.getText('UTF-8') : connection.errorStream?.getText('UTF-8')
                connection.disconnect()
                if (status < 200 || status >= 300) {
                    LOG.warn('Export metadata sync failed wlId={} status={} body={}', whiteLabelId, status, responseText)
                    return [status: status, body: [ok: false, error: "WL responded with status ${status}"]]
                }

                def parsed = responseText ? new JsonSlurper().parseText(responseText) : null
                Collection<Map> items = []
                if (parsed instanceof Map) {
                    items = (parsed.items instanceof Collection) ? (parsed.items as Collection<Map>) : []
                    if (parsed.count instanceof Number) {
                        totalCount = (parsed.count as Number).intValue()
                    }
                } else if (parsed instanceof Collection) {
                    items = parsed as Collection<Map>
                }

                if (items) {
                    if (mode == 'categories') {
                        totalImported += offerCategorySnapshotService.upsertAll(whiteLabelId, items, collectedAt)
                    } else {
                        totalImported += offerEntitySnapshotService.upsertAll(whiteLabelId, items, collectedAt)
                    }
                }

                if (items == null || items.isEmpty()) {
                    break
                }
                if (totalCount != null) {
                    offset += limit
                    if (offset >= totalCount) {
                        break
                    }
                } else if (items.size() < limit) {
                    break
                } else {
                    offset += limit
                }
            }
        } finally {
            if (injectedCorrelation) {
                MDC.remove('correlationId')
            }
        }

        INTEGRATION_LOG.info('CP->WL export metadata sync complete wlId={} mode={} count={}', whiteLabelId, mode, totalImported)
        return [status: HttpStatus.OK.value(), body: [ok: true, count: totalImported, wlId: whiteLabelId]]
    }

    private int resolveDefaultLimit() {
        def cfg = grailsApplication?.config?.controlPlane?.exportMetadataSync ?: [:]
        return (cfg.defaultLimit ?: 200) as int
    }

    private static String buildPagedUrl(String endpoint, int limit, int offset) {
        String separator = endpoint.contains('?') ? '&' : '?'
        return endpoint + separator + "limit=${limit}&offset=${offset}"
    }

    private static String normalizeUrl(String baseUrl, String path) {
        String trimmedBase = baseUrl?.trim()
        if (!trimmedBase) {
            return path
        }
        if (trimmedBase.endsWith('/') && path.startsWith('/')) {
            return trimmedBase.substring(0, trimmedBase.length() - 1) + path
        }
        if (!trimmedBase.endsWith('/') && !path.startsWith('/')) {
            return trimmedBase + '/' + path
        }
        return trimmedBase + path
    }
}

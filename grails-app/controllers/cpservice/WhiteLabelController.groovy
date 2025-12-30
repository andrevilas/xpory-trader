package cpservice

import grails.core.GrailsApplication
import grails.validation.ValidationException
import grails.converters.JSON
import groovy.json.JsonSlurper
import org.springframework.http.HttpStatus

class WhiteLabelController {

    static responseFormats = ['json']

    WhiteLabelRegistrationService whiteLabelRegistrationService
    WhiteLabelPolicyService whiteLabelPolicyService
    WhiteLabelIdentityService whiteLabelIdentityService
    PolicyMetricsService policyMetricsService
    JwtService jwtService
    GrailsApplication grailsApplication
    TraderAccountService traderAccountService
    GovernanceSigningService governanceSigningService
    GovernanceTelemetryService governanceTelemetryService

    def index() {
        Integer limit = params.int('limit') ?: 100
        Integer offset = params.int('offset') ?: 0
        limit = Math.min(limit, 200)
        String status = params.status
        String q = params.q

        List<WhiteLabel> whiteLabels = WhiteLabel.createCriteria().list(max: limit, offset: offset) {
            if (status) {
                eq('status', status)
            }
            if (q) {
                or {
                    ilike('name', "%${q}%")
                    ilike('id', "%${q}%")
                }
            }
            order('name', 'asc')
        }

        Number total = WhiteLabel.createCriteria().count {
            if (status) {
                eq('status', status)
            }
            if (q) {
                or {
                    ilike('name', "%${q}%")
                    ilike('id', "%${q}%")
                }
            }
        }

        render status: HttpStatus.OK.value(), contentType: "application/json", text: ([
                items: whiteLabels.collect { WhiteLabel wl ->
                    [
                            id            : wl.id,
                            name          : wl.name,
                            description   : wl.description,
                            contactEmail  : wl.contactEmail,
                            gatewayUrl    : wl.gatewayUrl,
                            status        : wl.status,
                            baselinePolicy: policyAsMap(wl.baselinePolicy)
                    ]
                },
                count: total
        ] as JSON)
    }

    def save() {
        Map payload = request.JSON as Map
        try {
            WhiteLabel wl = whiteLabelRegistrationService.register(payload)
            render status: HttpStatus.CREATED.value(), contentType: "application/json", text: ([
                    id            : wl.id,
                    name          : wl.name,
                    description   : wl.description,
                    contactEmail  : wl.contactEmail,
                    gatewayUrl    : wl.gatewayUrl,
                    status        : wl.status,
                    baselinePolicy: policyAsMap(wl.baselinePolicy)
            ] as JSON)
        } catch (IllegalArgumentException ex) {
            render status: HttpStatus.BAD_REQUEST.value(), contentType: "application/json", text: ([error: ex.message] as JSON)
        } catch (ValidationException ex) {
            render status: HttpStatus.UNPROCESSABLE_ENTITY.value(), contentType: "application/json", text: ([
                    error  : 'Validation failed',
                    details: ex.errors?.allErrors?.collect { it.defaultMessage }
            ] as JSON)
        }
    }

    def show() {
        String whiteLabelId = params.id
        WhiteLabel whiteLabel = WhiteLabel.get(whiteLabelId)
        if (!whiteLabel) {
            render status: HttpStatus.NOT_FOUND.value()
            return
        }

        render status: HttpStatus.OK.value(), contentType: "application/json", text: ([
                id            : whiteLabel.id,
                name          : whiteLabel.name,
                description   : whiteLabel.description,
                contactEmail  : whiteLabel.contactEmail,
                gatewayUrl    : whiteLabel.gatewayUrl,
                status        : whiteLabel.status,
                baselinePolicy: policyAsMap(whiteLabel.baselinePolicy)
        ] as JSON)
    }

    def update() {
        String whiteLabelId = params.id
        WhiteLabel whiteLabel = WhiteLabel.get(whiteLabelId)
        if (!whiteLabel) {
            render status: HttpStatus.NOT_FOUND.value()
            return
        }

        Map payload = request.JSON as Map ?: [:]
        if (payload.containsKey('id')) {
            String requestedId = payload.id?.toString()?.trim()
            if (!requestedId) {
                render status: HttpStatus.BAD_REQUEST.value(), contentType: "application/json", text: ([error: 'id is required'] as JSON)
                return
            }
            if (requestedId != whiteLabel.id) {
                try {
                    whiteLabel = whiteLabelIdentityService.renameWhiteLabelId(whiteLabel.id, requestedId)
                } catch (IllegalArgumentException ex) {
                    render status: HttpStatus.BAD_REQUEST.value(), contentType: "application/json", text: ([error: ex.message] as JSON)
                    return
                }
            }
        }
        if (payload.name != null) {
            whiteLabel.name = payload.name
        }
        if (payload.containsKey('description')) {
            whiteLabel.description = payload.description
        }
        if (payload.contactEmail != null) {
            whiteLabel.contactEmail = payload.contactEmail
        }
        if (payload.containsKey('gatewayUrl')) {
            whiteLabel.gatewayUrl = payload.gatewayUrl
        }
        if (payload.status != null) {
            whiteLabel.status = payload.status
        }

        try {
            whiteLabel.save(flush: true, failOnError: true)
            render status: HttpStatus.OK.value(), contentType: "application/json", text: ([
                    id            : whiteLabel.id,
                    name          : whiteLabel.name,
                    description   : whiteLabel.description,
                    contactEmail  : whiteLabel.contactEmail,
                    gatewayUrl    : whiteLabel.gatewayUrl,
                    status        : whiteLabel.status,
                    baselinePolicy: policyAsMap(whiteLabel.baselinePolicy)
            ] as JSON)
        } catch (ValidationException ex) {
            render status: HttpStatus.UNPROCESSABLE_ENTITY.value(), contentType: "application/json", text: ([
                    error  : 'Validation failed',
                    details: ex.errors?.allErrors?.collect { it.defaultMessage }
            ] as JSON)
        }
    }

    def policies() {
        String whiteLabelId = params.id
        def responseBody = policyMetricsService.timePolicyFetch {
            WhiteLabelPolicy policy = whiteLabelPolicyService.fetchBaseline(whiteLabelId)
            return policy ? policyAsMap(policy) : null
        }

        if (!responseBody) {
            render status: HttpStatus.NOT_FOUND.value()
            return
        }

        Map signature = governanceSigningService.signPayload(responseBody as Map)
        Map result = new LinkedHashMap(responseBody)
        result.signature = signature
        applyCacheHeaders()
        governanceTelemetryService.recordPolicyPackageSent(whiteLabelId, [signature: signature.value])
        render status: HttpStatus.OK.value(), contentType: "application/json", text: (result as JSON)
    }

    def updatePolicies() {
        String whiteLabelId = params.id
        Map payload = request.JSON as Map ?: [:]
        if (!payload.updatedBy) {
            payload.updatedBy = request.getHeader('X-Client-Id') ?: request.getHeader('X-Admin-Id')
        }
        if (!payload.updatedSource) {
            payload.updatedSource = request.getHeader('X-Client-Source') ?: request.getHeader('X-Source')
        }
        try {
            WhiteLabelPolicy policy = whiteLabelPolicyService.updateBaseline(whiteLabelId, payload)
        applyCacheHeaders()
        render status: HttpStatus.OK.value(), contentType: "application/json", text: (policyAsMap(policy) as JSON)
        } catch (IllegalArgumentException ex) {
            render status: HttpStatus.BAD_REQUEST.value(), contentType: "application/json", text: ([error: ex.message] as JSON)
        } catch (ValidationException ex) {
            render status: HttpStatus.UNPROCESSABLE_ENTITY.value(), contentType: "application/json", text: ([
                    error  : 'Validation failed',
                    details: ex.errors?.allErrors?.collect { it.defaultMessage }
            ] as JSON)
        }
    }

    def policyRevisions() {
        String whiteLabelId = params.id
        WhiteLabel whiteLabel = WhiteLabel.get(whiteLabelId)
        if (!whiteLabel) {
            render status: HttpStatus.NOT_FOUND.value()
            return
        }
        Integer limit = params.int('limit') ?: 20
        Integer offset = params.int('offset') ?: 0
        limit = Math.min(limit, 100)

        List<WhiteLabelPolicyRevision> revisions = WhiteLabelPolicyRevision.findAllByWhiteLabel(
                whiteLabel,
                [max: limit, offset: offset, sort: 'dateCreated', order: 'desc']
        )
        Number total = WhiteLabelPolicyRevision.countByWhiteLabel(whiteLabel)

        render status: HttpStatus.OK.value(), contentType: "application/json", text: ([
                items: revisions.collect { WhiteLabelPolicyRevision rev ->
                    [
                            id           : rev.id,
                            policyRevision: rev.policyRevision,
                            updatedBy    : rev.updatedBy,
                            updatedSource: rev.updatedSource,
                            effectiveFrom: rev.effectiveFrom,
                            dateCreated  : rev.dateCreated,
                            payload      : parsePayload(rev.payload)
                    ]
                },
                count: total,
                limit: limit,
                offset: offset
        ] as JSON)
    }

    def token() {
        String whiteLabelId = params.id
        WhiteLabel whiteLabel = WhiteLabel.get(whiteLabelId)
        if (!whiteLabel) {
            render status: HttpStatus.NOT_FOUND.value()
            return
        }
        Map payload = request.JSON as Map ?: [:]
        List<String> scopes = (payload.scopes ?: ['policies:read']) as List<String>
        String token = jwtService.issueToken(whiteLabel.id, scopes)
        render status: HttpStatus.CREATED.value(), contentType: "application/json", text: ([
                token            : token,
                expiresInSeconds : jwtService.tokenTtlSeconds
        ] as JSON)
    }

    private Map parsePayload(String payload) {
        if (!payload) {
            return [:]
        }
        try {
            Object parsed = new JsonSlurper().parseText(payload)
            return parsed instanceof Map ? (Map) parsed : [:]
        } catch (Exception ignored) {
            return [:]
        }
    }

    private Map policyAsMap(WhiteLabelPolicy policy) {
        if (!policy) {
            return null
        }
        Map result = [
                id                 : policy.id,
                whiteLabelId       : policy.whiteLabel.id,
                white_label_id     : policy.whiteLabel.id,
                importEnabled      : policy.importEnabled,
                exportEnabled      : policy.exportEnabled,
                exportDelaySeconds : policy.exportDelaySeconds,
                exportDelayDays    : policy.exportDelayDays,
                visibilityEnabled  : policy.visibilityEnabled,
                visibilityWls      : policy.visibilityWls,
                policyRevision     : policy.policyRevision,
                updatedBy          : policy.updatedBy,
                updatedSource      : policy.updatedSource,
                effectiveFrom      : policy.effectiveFrom,
                lastUpdated        : policy.lastUpdated,
                cacheTtlSeconds    : cacheTtlSeconds,
                import_enabled     : policy.importEnabled,
                export_enabled     : policy.exportEnabled,
                export_delay_days  : policy.exportDelayDays,
                visibility_wls     : policy.visibilityWls,
                updated_by         : policy.updatedBy,
                updated_source     : policy.updatedSource
        ]
        TraderAccount trader = traderAccountService?.findByWhiteLabelId(policy.whiteLabel.id)
        result.traderAccount = traderAccountService?.asPolicyPayload(trader)
        result
    }

    private void applyCacheHeaders() {
        int ttl = cacheTtlSeconds
        response.setHeader('Cache-Control', "private, max-age=${ttl}")
        response.setHeader('X-Policy-Cache-Ttl', ttl.toString())
    }

    private int getCacheTtlSeconds() {
        grailsApplication?.config?.getProperty('app.policy.cacheTtlSeconds', Integer, 300)
    }
}

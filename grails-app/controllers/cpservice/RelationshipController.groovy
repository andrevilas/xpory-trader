package cpservice

import grails.validation.ValidationException
import grails.converters.JSON
import org.springframework.http.HttpStatus

class RelationshipController {

    static responseFormats = ['json']

    RelationshipService relationshipService
    GovernanceSigningService governanceSigningService
    GovernanceTelemetryService governanceTelemetryService

    def index() {
        Integer limit = params.int('limit') ?: 100
        Integer offset = params.int('offset') ?: 0
        limit = Math.min(limit, 200)
        String sourceId = params.sourceId ?: params.src
        String targetId = params.targetId ?: params.dst ?: params.id ?: params.wlId
        String status = params.status
        boolean includeSignature = params.boolean('signed') || params.boolean('includeSignature')

        List<Relationship> relationships = Relationship.createCriteria().list(max: limit, offset: offset) {
            if (sourceId) {
                eq('sourceId', sourceId)
            }
            if (targetId) {
                eq('targetId', targetId)
            }
            if (status) {
                eq('status', status)
            }
            order('lastUpdated', 'desc')
        }

        Number total = Relationship.createCriteria().count {
            if (sourceId) {
                eq('sourceId', sourceId)
            }
            if (targetId) {
                eq('targetId', targetId)
            }
            if (status) {
                eq('status', status)
            }
        }

        render status: HttpStatus.OK.value(), contentType: "application/json", text: ([
                items: relationships.collect { Relationship rel ->
                    Map payload = toJson(rel)
                    if (includeSignature) {
                        payload.signature = governanceSigningService.signPayload(payload)
                    }
                    payload
                },
                count: total
        ] as JSON)
    }

    def show() {
        String src = params.src
        String dst = params.dst
        Relationship relationship = relationshipService.fetch(src, dst)
        if (!relationship) {
            render status: HttpStatus.NOT_FOUND.value()
            return
        }
        Map payload = toJson(relationship)
        Map signature = governanceSigningService.signPayload(payload)
        payload.signature = signature
        governanceTelemetryService.recordRelationshipPackageSent(relationship.sourceId, relationship.targetId, [signature: signature.value])
        render status: HttpStatus.OK.value(), contentType: "application/json", text: (payload as JSON)
    }

    def update() {
        String src = params.src
        String dst = params.dst
        Map payload = request.JSON as Map ?: [:]
        boolean hasExportPolicy = payload.containsKey('exportPolicy') || payload.containsKey('export_policy')
        if (!payload.updatedBy) {
            payload.updatedBy = request.getHeader('X-Client-Id') ?: request.getHeader('X-Admin-Id')
        }
        if (!payload.updatedSource) {
            payload.updatedSource = request.getHeader('X-Client-Source') ?: request.getHeader('X-Source')
        }
        try {
            Relationship relationship = relationshipService.upsert(src, dst, payload)
            Map body = toJson(relationship)
            Map signature = governanceSigningService.signPayload(body)
            body.signature = signature
            if (hasExportPolicy) {
                governanceTelemetryService.recordRelationshipExportPolicyUpdated(relationship.sourceId, relationship.targetId)
            }
            render status: HttpStatus.OK.value(), contentType: "application/json", text: (body as JSON)
        } catch (IllegalArgumentException ex) {
            render status: HttpStatus.BAD_REQUEST.value(), contentType: "application/json", text: ([error: ex.message] as JSON)
        } catch (ValidationException ex) {
            render status: HttpStatus.UNPROCESSABLE_ENTITY.value(), contentType: "application/json", text: ([
                    error  : 'Validation failed',
                    details: ex.errors?.allErrors?.collect { it.defaultMessage }
            ] as JSON)
        }
    }

    private Map toJson(Relationship relationship) {
        Map exportPolicy = relationship.exportPolicy
        [
                id                  : relationship.id,
                sourceId            : relationship.sourceId,
                targetId            : relationship.targetId,
                source_wl_id        : relationship.sourceId,
                dst_wl_id           : relationship.targetId,
                fxRate              : relationship.fxRate,
                fx_rate             : relationship.fxRate,
                limitAmount         : relationship.limitAmount,
                imbalance_limit     : relationship.limitAmount,
                status              : relationship.status?.toLowerCase(),
                notes               : relationship.notes,
                exportPolicy         : exportPolicy,
                export_policy        : exportPolicy,
                updatedBy           : relationship.updatedBy,
                updatedSource       : relationship.updatedSource,
                updatedAt           : relationship.lastUpdated,
                updated_at          : relationship.lastUpdated,
                updated_by          : relationship.updatedBy,
                updated_source      : relationship.updatedSource
        ]
    }
}

package cpservice

import grails.validation.ValidationException
import grails.converters.JSON
import org.springframework.http.HttpStatus

class RelationshipController {

    static responseFormats = ['json']

    RelationshipService relationshipService
    GovernanceSigningService governanceSigningService
    GovernanceTelemetryService governanceTelemetryService

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
        try {
            Relationship relationship = relationshipService.upsert(src, dst, payload)
            Map body = toJson(relationship)
            Map signature = governanceSigningService.signPayload(body)
            body.signature = signature
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
                updatedAt           : relationship.lastUpdated,
                updated_at          : relationship.lastUpdated
        ]
    }
}

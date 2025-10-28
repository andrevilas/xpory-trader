package cpservice

import grails.validation.ValidationException
import grails.converters.JSON
import org.springframework.http.HttpStatus

class ImbalanceController {

    static responseFormats = ['json']

    ImbalanceService imbalanceService

    def submit() {
        Map payload = request.JSON as Map ?: [:]
        try {
            ImbalanceSignal signal = imbalanceService.record(payload)
            render status: HttpStatus.ACCEPTED.value(), contentType: "application/json", text: ([
                    id          : signal.id,
                    sourceId    : signal.sourceId,
                    targetId    : signal.targetId,
                    action      : signal.action,
                    reason      : signal.reason,
                    initiatedBy : signal.initiatedBy,
                    effectiveFrom: signal.effectiveFrom,
                    effectiveUntil: signal.effectiveUntil,
                    acknowledged : signal.acknowledged
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

    def ack() {
        String signalId = params.id
        Map payload = request.JSON as Map ?: [:]
        String acknowledgedBy = payload.acknowledgedBy ?: request.getHeader('X-Client-Id') ?: 'unknown'
        try {
            ImbalanceSignal signal = imbalanceService.acknowledge(signalId, acknowledgedBy)
            render status: HttpStatus.OK.value(), contentType: "application/json", text: ([
                    id            : signal.id,
                    acknowledged  : signal.acknowledged,
                    acknowledgedBy: signal.acknowledgedBy,
                    acknowledgedAt: signal.acknowledgedAt
            ] as JSON)
        } catch (IllegalArgumentException ex) {
            render status: HttpStatus.BAD_REQUEST.value(), contentType: "application/json", text: ([error: ex.message] as JSON)
        }
    }
}

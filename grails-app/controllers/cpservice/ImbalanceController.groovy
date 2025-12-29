package cpservice

import grails.validation.ValidationException
import grails.converters.JSON
import org.springframework.http.HttpStatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ImbalanceController {

    static responseFormats = ['json']

    ImbalanceService imbalanceService
    ImbalanceDispatchService imbalanceDispatchService
    private static final Logger INTEGRATION_LOG = LoggerFactory.getLogger('cpservice.integration')

    def submit() {
        Map payload = request.JSON as Map ?: [:]
        if (!payload.updatedBy) {
            payload.updatedBy = request.getHeader('X-Client-Id') ?: request.getHeader('X-Admin-Id')
        }
        if (!payload.updatedSource) {
            payload.updatedSource = request.getHeader('X-Client-Source') ?: request.getHeader('X-Source')
        }
        try {
            ImbalanceSignal signal = imbalanceService.record(payload)
            INTEGRATION_LOG.info('CP imbalance signal recorded id={} sourceId={} targetId={} action={}',
                    signal.id, signal.sourceId, signal.targetId, signal.action)
            imbalanceDispatchService?.dispatch(signal)
            render status: HttpStatus.ACCEPTED.value(), contentType: "application/json", text: ([
                    id          : signal.id,
                    sourceId    : signal.sourceId,
                    targetId    : signal.targetId,
                    action      : signal.action,
                    reason      : signal.reason,
                    initiatedBy : signal.initiatedBy,
                    updatedBy   : signal.updatedBy,
                    updatedSource: signal.updatedSource,
                    effectiveFrom: signal.effectiveFrom,
                    effectiveUntil: signal.effectiveUntil,
                    acknowledged : signal.acknowledged,
                    dispatchStatus: signal.dispatchStatus,
                    dispatchAttempts: signal.dispatchAttempts,
                    dispatchError: signal.dispatchError
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
            INTEGRATION_LOG.info('CP imbalance signal acknowledged id={} by={}', signal.id, acknowledgedBy)
            render status: HttpStatus.OK.value(), contentType: "application/json", text: ([
                    id            : signal.id,
                    acknowledged  : signal.acknowledged,
                    acknowledgedBy: signal.acknowledgedBy,
                    acknowledgedAt: signal.acknowledgedAt,
                    dispatchStatus: signal.dispatchStatus,
                    updatedBy     : signal.updatedBy,
                    updatedSource : signal.updatedSource
            ] as JSON)
        } catch (IllegalArgumentException ex) {
            render status: HttpStatus.BAD_REQUEST.value(), contentType: "application/json", text: ([error: ex.message] as JSON)
        }
    }
}

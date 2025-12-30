package cpservice

import grails.converters.JSON
import org.springframework.http.HttpStatus

class TraderAccountController {

    static responseFormats = ['json']

    TraderAccountService traderAccountService
    TraderAccountSyncService traderAccountSyncService

    def show() {
        String whiteLabelId = params.id
        TraderAccount trader = traderAccountService.findByWhiteLabelId(whiteLabelId)
        if (!trader) {
            render status: HttpStatus.NOT_FOUND.value()
            return
        }
        render status: HttpStatus.OK.value(), contentType: 'application/json', text: (
                traderAccountService.asPolicyPayload(trader) as JSON
        )
    }

    def upsert() {
        String whiteLabelId = params.id
        WhiteLabel whiteLabel = WhiteLabel.get(whiteLabelId)
        if (!whiteLabel) {
            render status: HttpStatus.NOT_FOUND.value(), contentType: 'application/json', text: ([error: 'Unknown white label'] as JSON)
            return
        }
        Map payload = request.JSON as Map ?: [:]
        String admin = request.getHeader('X-Admin-Id') ?: payload.createdByAdmin
        try {
            TraderAccount trader = traderAccountService.upsert(whiteLabel, payload, admin?.toString())
            render status: HttpStatus.OK.value(), contentType: 'application/json', text: (
                    traderAccountService.asPolicyPayload(trader) as JSON
            )
        } catch (IllegalArgumentException ex) {
            render status: HttpStatus.BAD_REQUEST.value(), contentType: 'application/json', text: ([error: ex.message] as JSON)
        }
    }

    def sync() {
        String whiteLabelId = params.id
        String admin = request.getHeader('X-Admin-Id') ?: request.getHeader('X-Client-Id')
        String correlationId = request.getHeader('X-Correlation-Id') ?: request.getHeader('X-Request-Id')
        Map result = traderAccountSyncService.syncFromWhiteLabel(whiteLabelId, admin?.toString(), correlationId)
        render status: (result.status as int), contentType: 'application/json', text: (result.body as JSON)
    }
}

package cpservice

import grails.converters.JSON
import org.springframework.http.HttpStatus

class TraderAccountController {

    static responseFormats = ['json']

    TraderAccountService traderAccountService

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
}

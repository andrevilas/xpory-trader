package cpservice

import grails.converters.JSON
import org.springframework.http.HttpStatus

class TradeController {

    static responseFormats = ['json']

    TradeQueryService tradeQueryService
    TradeProjectionBackfillService tradeProjectionBackfillService

    def index() {
        Map result = tradeQueryService.list(params)
        render status: HttpStatus.OK.value(), contentType: 'application/json', text: (result as JSON)
    }

    def backfill() {
        Map result = tradeProjectionBackfillService.rebuild(params)
        render status: HttpStatus.OK.value(), contentType: 'application/json', text: ([
            ok       : true,
            scanned  : result.scanned,
            projected: result.projected,
            skipped  : result.skipped,
            totalTrades: result.totalTrades
        ] as JSON)
    }
}

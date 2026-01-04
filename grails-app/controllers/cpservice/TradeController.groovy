package cpservice

import grails.converters.JSON
import org.springframework.http.HttpStatus

class TradeController {

    static responseFormats = ['json']

    TradeQueryService tradeQueryService

    def index() {
        Map result = tradeQueryService.list(params)
        render status: HttpStatus.OK.value(), contentType: 'application/json', text: (result as JSON)
    }
}

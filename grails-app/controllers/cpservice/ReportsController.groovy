package cpservice

import grails.converters.JSON
import org.springframework.http.HttpStatus

class ReportsController {

    static responseFormats = ['json']

    ReportService reportService

    def tradeBalance() {
        Map summary = reportService.tradeBalanceSummary(params)
        render status: HttpStatus.OK.value(), contentType: "application/json", text: (summary as JSON)
    }
}

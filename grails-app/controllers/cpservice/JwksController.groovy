package cpservice

import grails.converters.JSON
import org.springframework.http.HttpStatus

class JwksController {

    static responseFormats = ['json']

    JwtKeyService jwtKeyService

    def index() {
        List<Map<String, Object>> keys = jwtKeyService.listActiveKeys().collect { key ->
            jwtKeyService.asJwk(key)
        }
        render status: HttpStatus.OK.value(), contentType: "application/json", text: ([keys: keys] as JSON)
    }
}


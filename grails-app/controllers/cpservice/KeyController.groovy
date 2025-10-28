package cpservice

import grails.converters.JSON
import org.springframework.http.HttpStatus

class KeyController {

    static responseFormats = ['json']

    JwtKeyService jwtKeyService

    def rotate() {
        String whiteLabelId = params.id
        if (!whiteLabelId) {
            render status: HttpStatus.BAD_REQUEST.value(), contentType: 'application/json', text: ([error: 'whiteLabelId required'] as JSON)
            return
        }
        try {
            WhiteLabelSigningKey key = jwtKeyService.rotateKeyFor(whiteLabelId)
            render status: HttpStatus.CREATED.value(), contentType: 'application/json', text: ([
                    whiteLabelId: key.whiteLabel.id,
                    keyId       : key.keyId,
                    algorithm   : key.algorithm,
                    validFrom   : key.validFrom,
                    validUntil  : key.validUntil
            ] as JSON)
        } catch (IllegalArgumentException ex) {
            render status: HttpStatus.BAD_REQUEST.value(), contentType: 'application/json', text: ([error: ex.message] as JSON)
        }
    }
}


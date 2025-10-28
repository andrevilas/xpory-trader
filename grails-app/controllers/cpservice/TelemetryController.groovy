package cpservice

import org.springframework.http.HttpStatus

class TelemetryController {

    static responseFormats = ['json']

    TelemetryIngestService telemetryIngestService

    def events() {
        def payload = request.JSON
        Collection<Map> events
        if (payload instanceof Collection) {
            events = payload as Collection<Map>
        } else if (payload instanceof Map && payload.events instanceof Collection) {
            events = payload.events as Collection<Map>
        } else if (payload instanceof Map) {
            events = [payload as Map]
        } else {
            render(status: HttpStatus.BAD_REQUEST.value(), contentType: 'application/json') {
                [error: 'Request body must contain telemetry event(s)']
            }
            return
        }

        try {
            List<TelemetryEvent> stored = telemetryIngestService.ingest(events)
            render(status: HttpStatus.ACCEPTED.value(), contentType: 'application/json') {
                [stored: stored.collect { [id: it.id, eventType: it.eventType, receivedAt: it.dateCreated] }]
            }
        } catch (IllegalArgumentException ex) {
            render(status: HttpStatus.BAD_REQUEST.value(), contentType: 'application/json') {
                [error: ex.message]
            }
        }
    }
}

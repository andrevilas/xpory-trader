package cpservice

import grails.converters.JSON
import org.springframework.http.HttpStatus

class TelemetryController {

    static responseFormats = ['json']

    TelemetryIngestService telemetryIngestService
    TelemetryQueryService telemetryQueryService

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

    def list() {
        try {
            Map result = telemetryQueryService.list(params)
            Map response = [
                    items : result.items.collect { TelemetryEvent event ->
                        [
                                id            : event.id,
                                whiteLabelId  : event.whiteLabelId,
                                nodeId        : event.nodeId,
                                eventType     : event.eventType,
                                eventTimestamp: event.eventTimestamp,
                                payload       : parsePayload(event.payload),
                                dateCreated   : event.dateCreated,
                                lastUpdated   : event.lastUpdated
                        ]
                    },
                    count : result.count,
                    limit : result.limit,
                    offset: result.offset
            ]
            render status: HttpStatus.OK.value(), contentType: 'application/json', text: (response as JSON)
        } catch (IllegalArgumentException ex) {
            render(status: HttpStatus.BAD_REQUEST.value(), contentType: 'application/json') {
                [error: ex.message]
            }
        }
    }

    private static Object parsePayload(String raw) {
        if (!raw) {
            return null
        }
        try {
            return JSON.parse(raw)
        } catch (Exception ignored) {
            return raw
        }
    }
}

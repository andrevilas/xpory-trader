package cpservice

import grails.gorm.transactions.Transactional

import java.time.OffsetDateTime

@Transactional(readOnly = true)
class TelemetryQueryService {

    Map list(Map params) {
        Integer limit = parseInt(params.limit, 50)
        Integer offset = parseInt(params.offset, 0)
        limit = Math.min(limit, 200)

        boolean fromProvided = hasValue(params.from)
        boolean toProvided = hasValue(params.to)
        Date from = parseDate(params.from)
        Date to = parseDate(params.to)

        if (fromProvided && !from) {
            throw new IllegalArgumentException('Invalid date format')
        }
        if (toProvided && !to) {
            throw new IllegalArgumentException('Invalid date format')
        }

        def filters = {
            if (params.whiteLabelId) {
                eq('whiteLabelId', params.whiteLabelId.toString())
            }
            if (params.eventType) {
                eq('eventType', params.eventType.toString())
            }
            if (params.nodeId) {
                eq('nodeId', params.nodeId.toString())
            }
            if (from) {
                ge('eventTimestamp', from)
            }
            if (to) {
                le('eventTimestamp', to)
            }
        }

        def items = TelemetryEvent.createCriteria().list(max: limit, offset: offset) {
            filters.delegate = delegate
            filters.call()
            order('eventTimestamp', 'desc')
        }

        int total = items?.totalCount ?: items.size()

        [
                items : items,
                count : total,
                limit : limit,
                offset: offset
        ]
    }

    private static Integer parseInt(Object value, int fallback) {
        if (value == null) {
            return fallback
        }
        try {
            return Integer.parseInt(value.toString())
        } catch (Exception ignored) {
            return fallback
        }
    }

    private static boolean hasValue(Object value) {
        if (value == null) {
            return false
        }
        if (value instanceof CharSequence) {
            return value.toString().trim()
        }
        if (value instanceof Collection) {
            return !value.isEmpty()
        }
        if (value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value) > 0
        }
        return true
    }

    private static Date parseDate(Object value) {
        if (!hasValue(value)) {
            return null
        }
        if (value instanceof Date) {
            return value
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value)
            if (length == 0) {
                return null
            }
            Object first = java.lang.reflect.Array.get(value, 0)
            return parseDate(first)
        }
        if (value instanceof Collection) {
            Iterator iterator = value.iterator()
            return iterator.hasNext() ? parseDate(iterator.next()) : null
        }
        try {
            return Date.from(OffsetDateTime.parse(value.toString()).toInstant())
        } catch (Exception ignored) {
            return null
        }
    }
}

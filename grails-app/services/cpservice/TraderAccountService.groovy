package cpservice

import grails.gorm.transactions.Transactional
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Transactional
class TraderAccountService {

    private static final Logger LOG = LoggerFactory.getLogger(TraderAccountService)

    TraderAccount upsert(WhiteLabel whiteLabel, Map payload, String createdByAdmin = null) {
        if (!whiteLabel) {
            throw new IllegalArgumentException('whiteLabel is required')
        }
        String desiredId = payload.id?.toString()?.trim()
        TraderAccount trader = null
        if (desiredId) {
            trader = TraderAccount.get(desiredId)
            if (trader && trader.whiteLabel?.id != whiteLabel.id) {
                throw new IllegalArgumentException('Trader id already belongs to another WL')
            }
        }
        if (!trader) {
            trader = TraderAccount.findByWhiteLabel(whiteLabel)
        }
        boolean isNew = !trader
        if (!trader) {
            trader = new TraderAccount(whiteLabel: whiteLabel)
            if (desiredId) {
                trader.id = desiredId
            }
        }

        String name = payload.name?.toString()?.trim()
        if (!name) {
            throw new IllegalArgumentException('Trader name is required')
        }
        trader.name = name

        if (payload.status) {
            trader.status = normaliseStatus(payload.status)
        }
        trader.contactEmail = payload.contactEmail?.toString()?.trim()
        trader.contactPhone = payload.contactPhone?.toString()?.trim()
        trader.createdByAdmin = createdByAdmin ?: payload.createdByAdmin?.toString()
        trader.issuedAt = payload.issuedAt instanceof Date ?
                (Date) payload.issuedAt : (payload.issuedAt ? parseDate(payload.issuedAt) : new Date())
        if (payload.confirmedAt) {
            trader.confirmedAt = payload.confirmedAt instanceof Date ?
                    (Date) payload.confirmedAt : parseDate(payload.confirmedAt)
        }

        trader.save(failOnError: true, flush: true)
        LOG.info("Trader account {} for WL {} {}", trader.id, whiteLabel.id, isNew ? 'created' : 'updated')
        trader
    }

    TraderAccount findByWhiteLabelId(String whiteLabelId) {
        if (!whiteLabelId) {
            return null
        }
        TraderAccount.findByWhiteLabel(WhiteLabel.get(whiteLabelId))
    }

    Map<String, Object> asPolicyPayload(TraderAccount trader) {
        if (!trader) {
            return null
        }
        [
                id           : trader.id,
                name         : trader.name,
                status       : trader.status,
                contactEmail : trader.contactEmail,
                contactPhone : trader.contactPhone,
                issuedAt     : trader.issuedAt,
                confirmedAt  : trader.confirmedAt,
                createdByAdmin: trader.createdByAdmin
        ]
    }

    void recordConfirmation(String whiteLabelId, String traderId, Date confirmedAt, String correlationId = null) {
        if (!whiteLabelId || !traderId) {
            return
        }
        TraderAccount trader = TraderAccount.findById(traderId)
        if (!trader || trader.whiteLabel?.id != whiteLabelId) {
            LOG.warn("Ignoring trader confirmation for unknown trader {} WL {} (correlation {})", traderId, whiteLabelId, correlationId)
            return
        }
        trader.confirmedAt = confirmedAt ?: new Date()
        trader.save(failOnError: true, flush: true)
        LOG.info("Trader account {} confirmed by WL {} (correlation {})", trader.id, whiteLabelId, correlationId)
    }

    void processTelemetry(TelemetryEvent event, Map payload) {
        if (!event || !payload) {
            return
        }
        if (!event.eventType?.equalsIgnoreCase('trader.account.confirmed')) {
            return
        }
        String traderId = (payload.cpTraderId ?: payload.traderId)?.toString()
        if (!traderId) {
            LOG.warn("Telemetry trader confirmation missing cpTraderId. Event {}", event.id)
            return
        }
        Date confirmedAt = payload.confirmedAt instanceof Date ?
                (Date) payload.confirmedAt : parseDate(payload.confirmedAt ?: event.eventTimestamp)
        recordConfirmation(event.whiteLabelId, traderId, confirmedAt, payload.correlationId?.toString())
    }

    private static Date parseDate(Object value) {
        if (value instanceof Date) {
            return (Date) value
        }
        if (value instanceof Number) {
            return new Date(((Number) value).longValue())
        }
        if (value) {
            try {
                return Date.from(java.time.OffsetDateTime.parse(value.toString()).toInstant())
            } catch (Exception ignored) {
                // fallthrough
            }
        }
        return new Date()
    }

    private static String normaliseStatus(Object raw) {
        String status = raw?.toString()?.toLowerCase()
        switch (status) {
            case 'paused':
            case 'inactive':
                return status
            case 'active':
            case 'enabled':
            case 'up':
                return 'active'
            default:
                return 'inactive'
        }
    }
}

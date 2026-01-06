package cpservice

import grails.gorm.transactions.Transactional

@Transactional
class OfferEntitySnapshotService {

    List<OfferEntitySnapshot> list(String exporterWlId, String search, boolean activeOnly, int limit, int offset) {
        OfferEntitySnapshot.createCriteria().list(max: limit, offset: offset) {
            eq('exporterWlId', exporterWlId)
            if (search) {
                ilike('name', "%${search}%")
            }
            if (activeOnly) {
                gt('activeOffersCount', 0)
            }
            order('name', 'asc')
        }
    }

    Number count(String exporterWlId, String search, boolean activeOnly) {
        OfferEntitySnapshot.createCriteria().count {
            eq('exporterWlId', exporterWlId)
            if (search) {
                ilike('name', "%${search}%")
            }
            if (activeOnly) {
                gt('activeOffersCount', 0)
            }
        }
    }

    int upsertAll(String exporterWlId, Collection<Map> entities, Date collectedAt) {
        if (!entities) {
            return 0
        }
        int updated = 0
        entities.each { Map item ->
            String entityId = item.entityId?.toString() ?: item.id?.toString()
            if (!entityId) {
                return
            }
            OfferEntitySnapshot snapshot = OfferEntitySnapshot.findByExporterWlIdAndEntityId(exporterWlId, entityId)
            if (!snapshot) {
                snapshot = new OfferEntitySnapshot(exporterWlId: exporterWlId, entityId: entityId)
            }
            snapshot.name = item.name?.toString()
            snapshot.activeOffersCount = (item.activeOffersCount ?: item.active_offers_count ?: 0) as Integer
            snapshot.status = item.status?.toString()
            snapshot.updatedAt = parseDate(item.updatedAt ?: item.updated_at)
            snapshot.collectedAt = collectedAt
            snapshot.save(failOnError: true, flush: true)
            updated++
        }
        updated
    }

    private Date parseDate(Object raw) {
        if (raw == null) {
            return null
        }
        if (raw instanceof Date) {
            return raw as Date
        }
        try {
            return Date.from(java.time.Instant.parse(raw.toString()))
        } catch (Exception ignored) {
            return null
        }
    }
}

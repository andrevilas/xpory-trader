package cpservice

import grails.gorm.transactions.Transactional

@Transactional
class OfferCategorySnapshotService {

    List<OfferCategorySnapshot> list(String exporterWlId, String search, boolean activeOnly, int limit, int offset) {
        OfferCategorySnapshot.createCriteria().list(max: limit, offset: offset) {
            eq('exporterWlId', exporterWlId)
            if (search) {
                ilike('categoryName', "%${search}%")
            }
            if (activeOnly) {
                gt('activeCount', 0)
            }
            order('categoryName', 'asc')
        }
    }

    Number count(String exporterWlId, String search, boolean activeOnly) {
        OfferCategorySnapshot.createCriteria().count {
            eq('exporterWlId', exporterWlId)
            if (search) {
                ilike('categoryName', "%${search}%")
            }
            if (activeOnly) {
                gt('activeCount', 0)
            }
        }
    }

    int upsertAll(String exporterWlId, Collection<Map> categories, Date collectedAt) {
        if (!categories) {
            return 0
        }
        int updated = 0
        categories.each { Map item ->
            String categoryId = item.categoryId?.toString() ?: item.id?.toString()
            if (!categoryId) {
                return
            }
            OfferCategorySnapshot snapshot = OfferCategorySnapshot.findByExporterWlIdAndCategoryId(exporterWlId, categoryId)
            if (!snapshot) {
                snapshot = new OfferCategorySnapshot(exporterWlId: exporterWlId, categoryId: categoryId)
            }
            snapshot.categoryName = item.name?.toString() ?: item.categoryName?.toString()
            snapshot.activeCount = (item.activeCount ?: item.active_count ?: 0) as Integer
            snapshot.collectedAt = collectedAt
            snapshot.save(failOnError: true, flush: true)
            updated++
        }
        updated
    }
}

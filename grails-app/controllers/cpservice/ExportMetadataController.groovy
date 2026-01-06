package cpservice

import grails.converters.JSON
import org.springframework.http.HttpStatus

class ExportMetadataController {

    static responseFormats = ['json']

    OfferCategorySnapshotService offerCategorySnapshotService
    OfferEntitySnapshotService offerEntitySnapshotService
    ExportMetadataSyncService exportMetadataSyncService

    def categories() {
        String whiteLabelId = params.id
        if (!WhiteLabel.get(whiteLabelId)) {
            render status: HttpStatus.NOT_FOUND.value(), contentType: 'application/json', text: ([error: 'Unknown white label'] as JSON)
            return
        }
        int limit = Math.min(params.int('limit') ?: 200, 1000)
        int offset = Math.max(params.int('offset') ?: 0, 0)
        String search = params.search?.toString()?.trim()
        boolean activeOnly = params.boolean('activeOnly')

        List<OfferCategorySnapshot> items = offerCategorySnapshotService.list(whiteLabelId, search, activeOnly, limit, offset)
        Number total = offerCategorySnapshotService.count(whiteLabelId, search, activeOnly)
        render status: HttpStatus.OK.value(), contentType: 'application/json', text: ([
                items: items.collect { OfferCategorySnapshot snapshot ->
                    [
                            exporterWlId: snapshot.exporterWlId,
                            categoryId  : snapshot.categoryId,
                            name        : snapshot.categoryName,
                            activeCount : snapshot.activeCount,
                            collectedAt : snapshot.collectedAt
                    ]
                },
                count: total
        ] as JSON)
    }

    def syncCategories() {
        String whiteLabelId = params.id
        String admin = request.getHeader('X-Admin-Id') ?: request.getHeader('X-Client-Id')
        String correlationId = request.getHeader('X-Correlation-Id') ?: request.getHeader('X-Request-Id')
        Map result = exportMetadataSyncService.syncCategories(whiteLabelId, admin?.toString(), correlationId)
        render status: (result.status as int), contentType: 'application/json', text: (result.body as JSON)
    }

    def entities() {
        String whiteLabelId = params.id
        if (!WhiteLabel.get(whiteLabelId)) {
            render status: HttpStatus.NOT_FOUND.value(), contentType: 'application/json', text: ([error: 'Unknown white label'] as JSON)
            return
        }
        int limit = Math.min(params.int('limit') ?: 200, 1000)
        int offset = Math.max(params.int('offset') ?: 0, 0)
        String search = params.search?.toString()?.trim()
        boolean activeOnly = params.boolean('activeOnly')

        List<OfferEntitySnapshot> items = offerEntitySnapshotService.list(whiteLabelId, search, activeOnly, limit, offset)
        Number total = offerEntitySnapshotService.count(whiteLabelId, search, activeOnly)
        render status: HttpStatus.OK.value(), contentType: 'application/json', text: ([
                items: items.collect { OfferEntitySnapshot snapshot ->
                    [
                            exporterWlId      : snapshot.exporterWlId,
                            entityId          : snapshot.entityId,
                            name              : snapshot.name,
                            activeOffersCount : snapshot.activeOffersCount,
                            status            : snapshot.status,
                            updatedAt         : snapshot.updatedAt,
                            collectedAt       : snapshot.collectedAt
                    ]
                },
                count: total
        ] as JSON)
    }

    def syncEntities() {
        String whiteLabelId = params.id
        String admin = request.getHeader('X-Admin-Id') ?: request.getHeader('X-Client-Id')
        String correlationId = request.getHeader('X-Correlation-Id') ?: request.getHeader('X-Request-Id')
        Map result = exportMetadataSyncService.syncEntities(whiteLabelId, admin?.toString(), correlationId)
        render status: (result.status as int), contentType: 'application/json', text: (result.body as JSON)
    }
}

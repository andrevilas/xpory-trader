package cpservice

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode(includes = 'id')
@ToString(includes = ['id', 'exporterWlId', 'entityId'], includeNames = true)
class OfferEntitySnapshot {

    String id
    String exporterWlId
    String entityId
    String name
    Integer activeOffersCount = 0
    String status
    Date updatedAt
    Date collectedAt

    Date dateCreated
    Date lastUpdated

    static mapping = {
        table 'cp_entity_snapshots'
        id generator: 'uuid2', type: 'string', length: 36
        exporterWlId column: 'exporter_wl_id'
        entityId column: 'entity_id'
        activeOffersCount column: 'active_offers_count'
        collectedAt column: 'collected_at'
        updatedAt column: 'updated_at'
        exporterWlId index: 'idx_cp_entity_snap_exporter'
    }

    static constraints = {
        exporterWlId blank: false, maxSize: 36
        entityId blank: false, maxSize: 120
        name nullable: true, maxSize: 200
        activeOffersCount nullable: false, min: 0
        status nullable: true, maxSize: 40
        updatedAt nullable: true
        collectedAt nullable: false
        exporterWlId unique: 'entityId'
    }
}

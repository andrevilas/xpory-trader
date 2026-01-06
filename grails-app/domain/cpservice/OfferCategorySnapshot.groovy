package cpservice

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode(includes = 'id')
@ToString(includes = ['id', 'exporterWlId', 'categoryId'], includeNames = true)
class OfferCategorySnapshot {

    String id
    String exporterWlId
    String categoryId
    String categoryName
    Integer activeCount = 0
    Date collectedAt

    Date dateCreated
    Date lastUpdated

    static mapping = {
        table 'cp_offer_category_snapshots'
        id generator: 'uuid2', type: 'string', length: 36
        exporterWlId column: 'exporter_wl_id'
        categoryId column: 'category_id'
        categoryName column: 'category_name'
        activeCount column: 'active_count'
        collectedAt column: 'collected_at'
        exporterWlId index: 'idx_cp_cat_snap_exporter'
    }

    static constraints = {
        exporterWlId blank: false, maxSize: 36
        categoryId blank: false, maxSize: 120
        categoryName nullable: true, maxSize: 200
        activeCount nullable: false, min: 0
        collectedAt nullable: false
        exporterWlId unique: 'categoryId'
    }
}

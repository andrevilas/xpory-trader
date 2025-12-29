package cpservice

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode(includes = 'id')
@ToString(includes = ['id', 'sourceId', 'targetId'], includeNames = true)
class Relationship {

    String id
    String sourceId
    String targetId
    BigDecimal fxRate = BigDecimal.ONE
    BigDecimal limitAmount = BigDecimal.ZERO
    String status = 'active'
    String notes
    String updatedBy
    String updatedSource

    Date dateCreated
    Date lastUpdated

    static mapping = {
        table 'cp_relationships'
        id generator: 'uuid2', type: 'string', length: 36
        fxRate scale: 6, precision: 18
        limitAmount scale: 2, precision: 18
        sourceId index: 'idx_cp_relationship_src'
        targetId index: 'idx_cp_relationship_dst'
    }

    static constraints = {
        sourceId blank: false, maxSize: 36
        targetId blank: false, maxSize: 36
        fxRate nullable: false, min: 0.000001G
        limitAmount nullable: false, min: 0.0G
        status inList: ['active', 'blocked', 'inactive', 'paused']
        notes nullable: true, maxSize: 500
        updatedBy nullable: true, maxSize: 120
        updatedSource nullable: true, maxSize: 120
        targetId unique: 'sourceId', validator: { val, obj ->
            if (val == obj.sourceId) {
                return 'relationship.sameParty'
            }
        }
    }
}

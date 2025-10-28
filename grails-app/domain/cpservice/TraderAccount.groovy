package cpservice

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * Trader Account metadata owned by the Control Plane and synchronised with WL nodes.
 */
@EqualsAndHashCode(includes = 'id')
@ToString(includes = ['id', 'whiteLabel', 'status'], includeNames = true)
class TraderAccount {

    String id
    WhiteLabel whiteLabel
    String name
    String status = 'active'
    String contactEmail
    String contactPhone
    String createdByAdmin
    Date issuedAt = new Date()
    Date confirmedAt

    Date dateCreated
    Date lastUpdated

    static belongsTo = [whiteLabel: WhiteLabel]

    static mapping = {
        table 'cp_trader_accounts'
        id generator: 'uuid2', type: 'string', length: 36
        status index: 'idx_cp_trader_status'
        issuedAt column: 'issued_at'
        confirmedAt column: 'confirmed_at'
        createdByAdmin column: 'created_by_admin'
        contactEmail column: 'contact_email'
        contactPhone column: 'contact_phone'
    }

    static constraints = {
        whiteLabel nullable: false, unique: true
        name blank: false, maxSize: 120
        status blank: false, inList: ['active', 'paused', 'inactive']
        contactEmail nullable: true, maxSize: 150
        contactPhone nullable: true, maxSize: 60
        createdByAdmin nullable: true, maxSize: 120
        issuedAt nullable: false
        confirmedAt nullable: true
    }
}

package cpservice

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * Represents a whitelabel partner that is governed by the control plane.
 */
@EqualsAndHashCode(includes = 'id')
@ToString(includes = ['id', 'name'], includeNames = true)
class WhiteLabel {

    String id
    String name
    String description
    String contactEmail
    String gatewayUrl
    String status = 'active'

    Date dateCreated
    Date lastUpdated

    static hasOne = [baselinePolicy: WhiteLabelPolicy]

    static mapping = {
        table 'cp_white_labels'
        id generator: 'uuid2', type: 'string', length: 36
        baselinePolicy cascade: 'all-delete-orphan'
    }

    static constraints = {
        name blank: false, unique: true, maxSize: 120
        description nullable: true, maxSize: 500
        contactEmail blank: false, email: true, maxSize: 150
        gatewayUrl nullable: true, maxSize: 500
        status inList: ['active', 'inactive']
    }
}

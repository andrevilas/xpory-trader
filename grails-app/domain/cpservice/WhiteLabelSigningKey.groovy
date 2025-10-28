package cpservice

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode(includes = 'id')
@ToString(includes = ['id', 'whiteLabel', 'keyId', 'active'], includeNames = true)
class WhiteLabelSigningKey {

    String id
    WhiteLabel whiteLabel
    String keyId
    String algorithm = 'RS256'
    String publicKey // Base64 DER (X.509)
    String privateKey // Base64 DER (PKCS8)
    boolean active = true
    Date validFrom = new Date()
    Date validUntil

    Date dateCreated
    Date lastUpdated

    static belongsTo = [whiteLabel: WhiteLabel]

    static mapping = {
        table 'cp_white_label_keys'
        id generator: 'uuid2', type: 'string', length: 36
        publicKey type: 'text'
        privateKey type: 'text'
        keyId index: 'idx_cp_wl_keys_kid'
    }

    static constraints = {
        keyId blank: false, maxSize: 80, unique: true
        algorithm blank: false
        publicKey blank: false
        privateKey blank: false
        validUntil nullable: true
    }
}


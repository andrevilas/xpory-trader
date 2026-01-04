package cpservice

import groovy.transform.ToString

@ToString(includeNames = true, includes = ['id', 'email', 'name', 'role', 'status'])
class AdminUser {

    static final String ROLE_MASTER = 'MASTER'
    static final String ROLE_TRADER = 'TRADER'
    static final String ROLE_MANAGER = 'MANAGER'

    static final String STATUS_ACTIVE = 'active'
    static final String STATUS_DISABLED = 'disabled'

    String id
    String email
    String name
    String phone
    String passwordHash
    String role
    String status = STATUS_ACTIVE
    Date lastLoginAt

    Date dateCreated
    Date lastUpdated

    static mapping = {
        table 'cp_users'
        version false
        id generator: 'uuid2', type: 'string', length: 36
        passwordHash column: 'password_hash'
        lastLoginAt column: 'last_login_at'
    }

    static constraints = {
        email blank: false, maxSize: 200, unique: true
        name nullable: true, maxSize: 200
        phone nullable: true, maxSize: 40
        passwordHash blank: false
        role blank: false, inList: [ROLE_MASTER, ROLE_TRADER, ROLE_MANAGER]
        status blank: false, inList: [STATUS_ACTIVE, STATUS_DISABLED]
        lastLoginAt nullable: true
    }
}

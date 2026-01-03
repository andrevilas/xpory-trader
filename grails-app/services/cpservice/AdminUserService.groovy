package cpservice

import grails.core.GrailsApplication
import grails.gorm.transactions.Transactional
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@Transactional
class AdminUserService {

    GrailsApplication grailsApplication

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12)

    AdminUser createUser(String email, String password, String role) {
        validateEmail(email)
        if (!password) {
            throw new IllegalArgumentException('password is required')
        }
        AdminUser user = new AdminUser(
                email: normalizeEmail(email),
                passwordHash: PASSWORD_ENCODER.encode(password),
                role: role ?: AdminUser.ROLE_TRADER,
                status: AdminUser.STATUS_ACTIVE
        )
        user.save(flush: true, failOnError: true)
        return user
    }

    AdminUser updateUser(String userId, Map payload) {
        AdminUser user = AdminUser.get(userId)
        if (!user) {
            throw new IllegalArgumentException('user not found')
        }
        if (payload.containsKey('email')) {
            String email = payload.email?.toString()
            validateEmail(email)
            user.email = normalizeEmail(email)
        }
        if (payload.containsKey('role')) {
            user.role = payload.role?.toString()?.trim()
        }
        if (payload.containsKey('status')) {
            user.status = payload.status?.toString()?.trim()
        }
        user.save(flush: true, failOnError: true)
        return user
    }

    AdminUser resetPassword(String userId, String password) {
        AdminUser user = AdminUser.get(userId)
        if (!user) {
            throw new IllegalArgumentException('user not found')
        }
        if (!password) {
            throw new IllegalArgumentException('password is required')
        }
        user.passwordHash = PASSWORD_ENCODER.encode(password)
        user.save(flush: true, failOnError: true)
        return user
    }

    AdminUser authenticate(String email, String password) {
        if (!email || !password) {
            return null
        }
        AdminUser user = AdminUser.findByEmail(normalizeEmail(email))
        if (!user || user.status != AdminUser.STATUS_ACTIVE) {
            return null
        }
        if (!PASSWORD_ENCODER.matches(password, user.passwordHash)) {
            return null
        }
        user.lastLoginAt = new Date()
        user.save(flush: true, failOnError: true)
        return user
    }

    void bootstrapUsersIfNeeded() {
        Map cfg = grailsApplication?.config?.security?.adminUsers ?: [:]
        String masterEmail = cfg?.bootstrap?.masterEmail ?: 'master@xpory.local'
        String masterPassword = cfg?.bootstrap?.masterPassword ?: 'changeit'
        String traderEmail = cfg?.bootstrap?.traderEmail ?: 'trader@xpory.local'
        String traderPassword = cfg?.bootstrap?.traderPassword ?: 'changeit'

        ensureUser(masterEmail, masterPassword, AdminUser.ROLE_MASTER)
        ensureUser(traderEmail, traderPassword, AdminUser.ROLE_TRADER)
    }

    List<AdminUser> listUsers(Integer limit = 100, Integer offset = 0) {
        int safeLimit = Math.min(limit ?: 100, 200)
        int safeOffset = Math.max(offset ?: 0, 0)
        return AdminUser.list(max: safeLimit, offset: safeOffset, sort: 'email', order: 'asc')
    }

    Number countUsers() {
        return AdminUser.count()
    }

    private void ensureUser(String email, String password, String role) {
        String normalized = normalizeEmail(email)
        AdminUser existing = AdminUser.findByEmail(normalized)
        if (existing) {
            return
        }
        createUser(normalized, password, role)
    }

    private static String normalizeEmail(String email) {
        return email?.toString()?.trim()?.toLowerCase()
    }

    private static void validateEmail(String email) {
        if (!email) {
            throw new IllegalArgumentException('email is required')
        }
        if (!email.toString().contains('@')) {
            throw new IllegalArgumentException('email is invalid')
        }
    }
}

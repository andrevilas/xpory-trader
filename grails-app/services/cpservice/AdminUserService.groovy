package cpservice

import grails.core.GrailsApplication
import grails.gorm.transactions.Transactional
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@Transactional
class AdminUserService {

    GrailsApplication grailsApplication

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12)

    AdminUser createUser(String email, String password, String role, String name = null, String phone = null) {
        validateEmail(email)
        if (!password) {
            throw new IllegalArgumentException('password is required')
        }
        AdminUser user = new AdminUser(
                email: normalizeEmail(email),
                name: normalizeName(name),
                phone: normalizePhone(phone),
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
            user.role = payload.role?.toString()?.trim()?.toUpperCase()
        }
        if (payload.containsKey('status')) {
            user.status = normalizeStatus(payload.status)
        }
        if (payload.containsKey('name')) {
            user.name = normalizeName(payload.name)
        }
        if (payload.containsKey('phone')) {
            user.phone = normalizePhone(payload.phone)
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

    AdminUser updateProfile(String userId, Map payload) {
        AdminUser user = AdminUser.get(userId)
        if (!user) {
            throw new IllegalArgumentException('user not found')
        }
        if (payload.containsKey('email') || payload.containsKey('role') || payload.containsKey('status')) {
            throw new IllegalArgumentException('only name and phone can be updated')
        }
        if (!payload.containsKey('name') && !payload.containsKey('phone')) {
            throw new IllegalArgumentException('no profile fields provided')
        }
        if (payload.containsKey('name')) {
            user.name = normalizeName(payload.name)
        }
        if (payload.containsKey('phone')) {
            user.phone = normalizePhone(payload.phone)
        }
        user.save(flush: true, failOnError: true)
        return user
    }

    AdminUser updatePassword(String userId, String currentPassword, String newPassword) {
        AdminUser user = AdminUser.get(userId)
        if (!user) {
            throw new IllegalArgumentException('user not found')
        }
        if (!currentPassword) {
            throw new IllegalArgumentException('currentPassword is required')
        }
        if (!newPassword) {
            throw new IllegalArgumentException('newPassword is required')
        }
        if (!PASSWORD_ENCODER.matches(currentPassword, user.passwordHash)) {
            throw new IllegalArgumentException('currentPassword is invalid')
        }
        user.passwordHash = PASSWORD_ENCODER.encode(newPassword)
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
        String masterEmail = resolveBootstrapValue(cfg?.bootstrap?.masterEmail, 'master@xpory.local')
        String masterPassword = resolveBootstrapValue(cfg?.bootstrap?.masterPassword, 'changeit')
        String traderEmail = resolveBootstrapValue(cfg?.bootstrap?.traderEmail, 'trader@xpory.local')
        String traderPassword = resolveBootstrapValue(cfg?.bootstrap?.traderPassword, 'changeit')
        String managerEmail = resolveBootstrapValue(cfg?.bootstrap?.managerEmail, 'manager@xpory.local')
        String managerPassword = resolveBootstrapValue(cfg?.bootstrap?.managerPassword, 'changeit')

        ensureUser(masterEmail, masterPassword, AdminUser.ROLE_MASTER)
        ensureUser(traderEmail, traderPassword, AdminUser.ROLE_TRADER)
        ensureUser(managerEmail, managerPassword, AdminUser.ROLE_MANAGER)
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

    private static String resolveBootstrapValue(Object value, String fallback) {
        String normalized = value?.toString()?.trim()
        return normalized ?: fallback
    }

    private static String normalizeEmail(String email) {
        return email?.toString()?.trim()?.toLowerCase()
    }

    private static String normalizeName(Object name) {
        String normalized = name?.toString()?.trim()
        return normalized ?: null
    }

    private static String normalizePhone(Object phone) {
        String normalized = phone?.toString()?.trim()
        return normalized ?: null
    }

    private static String normalizeStatus(Object status) {
        if (status == null) {
            return null
        }
        if (status instanceof Boolean) {
            return status ? AdminUser.STATUS_ACTIVE : AdminUser.STATUS_DISABLED
        }
        String normalized = status.toString().trim().toLowerCase()
        if (normalized == 'true') {
            return AdminUser.STATUS_ACTIVE
        }
        if (normalized == 'false') {
            return AdminUser.STATUS_DISABLED
        }
        return normalized
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

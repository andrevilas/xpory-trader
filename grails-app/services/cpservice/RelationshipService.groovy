package cpservice

import grails.gorm.transactions.Transactional
import groovy.json.JsonSlurper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.BigDecimal
import java.time.Instant

@Transactional
class RelationshipService {

    private static final Logger LOG = LoggerFactory.getLogger(RelationshipService)

    Relationship upsert(String sourceId, String targetId, Map payload) {
        if (!sourceId || !targetId) {
            throw new IllegalArgumentException('source and target are required')
        }
        Relationship rel = Relationship.findBySourceIdAndTargetId(sourceId, targetId)
        if (!rel) {
            rel = new Relationship(sourceId: sourceId, targetId: targetId)
        }

        if (payload.containsKey('fxRate')) {
            rel.fxRate = toBigDecimal(payload.fxRate)
        } else if (payload.containsKey('fx_rate')) {
            rel.fxRate = toBigDecimal(payload.fx_rate)
        }
        if (payload.containsKey('limitAmount')) {
            rel.limitAmount = toBigDecimal(payload.limitAmount)
        } else if (payload.containsKey('imbalanceLimit')) {
            rel.limitAmount = toBigDecimal(payload.imbalanceLimit)
        } else if (payload.containsKey('imbalance_limit')) {
            rel.limitAmount = toBigDecimal(payload.imbalance_limit)
        }
        if (payload.containsKey('status')) {
            rel.status = payload.status?.toString()?.toLowerCase()
        }
        if (payload.containsKey('notes')) {
            rel.notes = payload.notes?.toString()
        }
        if (payload.containsKey('updatedBy') || payload.containsKey('updated_by')) {
            rel.updatedBy = (payload.updatedBy ?: payload.updated_by)?.toString()
        }
        if (payload.containsKey('updatedSource') || payload.containsKey('updated_source') || payload.containsKey('source')) {
            rel.updatedSource = (payload.updatedSource ?: payload.updated_source ?: payload.source)?.toString()
        }

        if (payload.containsKey('exportPolicy') || payload.containsKey('export_policy')) {
            Object rawPolicy = payload.containsKey('exportPolicy') ? payload.exportPolicy : payload.export_policy
            Map parsedPolicy = parseExportPolicy(rawPolicy)
            if (parsedPolicy != null) {
                if (isValidExportPolicy(parsedPolicy)) {
                    rel.exportPolicy = parsedPolicy
                } else {
                    LOG.warn('Invalid export_policy for relationship {} -> {}, ignoring update', sourceId, targetId)
                }
            } else if (rawPolicy == null) {
                rel.exportPolicy = null
            }
        }

        rel.save(failOnError: true, flush: true)
        rel
    }

    Relationship fetch(String sourceId, String targetId) {
        if (!sourceId || !targetId) {
            throw new IllegalArgumentException('source and target are required')
        }
        Relationship.findBySourceIdAndTargetId(sourceId, targetId)
    }

    protected BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null
        }
        if (value instanceof BigDecimal) {
            return value
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString())
        }
        try {
            return new BigDecimal(value.toString())
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException('Numeric value expected')
        }
    }

    private Map parseExportPolicy(Object rawPolicy) {
        if (rawPolicy == null) {
            return null
        }
        if (rawPolicy instanceof Map) {
            return rawPolicy as Map
        }
        if (rawPolicy instanceof CharSequence) {
            String text = rawPolicy.toString().trim()
            if (!text) {
                return null
            }
            def parsed = new JsonSlurper().parseText(text)
            if (parsed instanceof Map) {
                return parsed as Map
            }
            return null
        }
        return null
    }

    private boolean isValidExportPolicy(Map policy) {
        if (policy == null) {
            return true
        }
        if (policy.containsKey('enabled') && !(policy.enabled instanceof Boolean)) {
            return false
        }
        if (!isValidBoolean(policy, 'include_domestic') || !isValidBoolean(policy, 'include_under_budget')) {
            return false
        }
        if (!isValidStringArray(policy, 'include_categories') || !isValidStringArray(policy, 'exclude_categories')) {
            return false
        }
        if (!isValidStringArray(policy, 'entity_allowlist') || !isValidStringArray(policy, 'entity_blocklist')) {
            return false
        }
        if (!isValidMinCreatedAt(policy)) {
            return false
        }
        if (!isValidPriceRange(policy)) {
            return false
        }
        return true
    }

    private boolean isValidBoolean(Map policy, String key) {
        if (!policy.containsKey(key)) {
            return true
        }
        return policy[key] instanceof Boolean
    }

    private boolean isValidStringArray(Map policy, String key) {
        if (!policy.containsKey(key)) {
            return true
        }
        Object value = policy[key]
        if (!(value instanceof Collection)) {
            return false
        }
        return (value as Collection).every { it instanceof CharSequence && it.toString().trim() }
    }

    private boolean isValidMinCreatedAt(Map policy) {
        String raw = policy.min_created_at?.toString()
        if (!raw) {
            raw = policy.minCreatedAt?.toString()
        }
        if (!raw) {
            return true
        }
        if (!raw.endsWith('Z')) {
            return false
        }
        try {
            Instant.parse(raw)
            return true
        } catch (Exception ignored) {
            return false
        }
    }

    private boolean isValidPriceRange(Map policy) {
        Object minRaw = policy.containsKey('price_min') ? policy.price_min : policy.priceMin
        Object maxRaw = policy.containsKey('price_max') ? policy.price_max : policy.priceMax
        BigDecimal minVal = null
        BigDecimal maxVal = null
        try {
            if (minRaw != null) {
                minVal = toBigDecimal(minRaw)
            }
            if (maxRaw != null) {
                maxVal = toBigDecimal(maxRaw)
            }
        } catch (IllegalArgumentException ignored) {
            return false
        }
        if (minVal != null && maxVal != null) {
            return minVal.compareTo(maxVal) <= 0
        }
        return true
    }
}

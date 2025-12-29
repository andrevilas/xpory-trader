package cpservice

import grails.gorm.transactions.Transactional
import groovy.json.JsonOutput

@Transactional(readOnly = true)
class WhiteLabelPolicyService {

    PolicyMetricsService policyMetricsService

    WhiteLabelPolicy fetchBaseline(String whiteLabelId) {
        if (!whiteLabelId) {
            throw new IllegalArgumentException('whiteLabelId is required')
        }
        WhiteLabel whiteLabel = WhiteLabel.get(whiteLabelId)
        if (!whiteLabel) {
            return null
        }
        return whiteLabel.baselinePolicy
    }

    List<WhiteLabelPolicy> listBaselines(Collection<String> whiteLabelIds = null, Date changedSince = null) {
        def criteria = WhiteLabelPolicy.createCriteria()
        criteria.list {
            if (whiteLabelIds) {
                inList('whiteLabel.id', whiteLabelIds)
            }
            if (changedSince) {
                ge('lastUpdated', changedSince)
            }
            order('lastUpdated', 'desc')
        }
    }
    @Transactional
    WhiteLabelPolicy updateBaseline(String whiteLabelId, Map payload) {
        if (!whiteLabelId) {
            throw new IllegalArgumentException('whiteLabelId is required')
        }
        WhiteLabel whiteLabel = WhiteLabel.get(whiteLabelId)
        if (!whiteLabel) {
            throw new IllegalArgumentException('Unknown white label id')
        }

        WhiteLabelPolicy policy = whiteLabel.baselinePolicy
        if (!policy) {
            policy = new WhiteLabelPolicy(whiteLabel: whiteLabel)
        }

        boolean drifted = false
        String updatedBy = payload.updatedBy ?: payload.updated_by
        String updatedSource = payload.updatedSource ?: payload.updated_source ?: payload.source

        if (payload.containsKey('importEnabled')) {
            boolean newVal = toBoolean(payload.importEnabled)
            if (newVal != policy.importEnabled) {
                drifted = true
                policy.importEnabled = newVal
            }
        }
        if (payload.containsKey('exportEnabled')) {
            boolean newVal = toBoolean(payload.exportEnabled)
            if (newVal != policy.exportEnabled) {
                drifted = true
                policy.exportEnabled = newVal
            }
        }
        if (payload.containsKey('exportDelaySeconds')) {
            Integer newVal = (payload.exportDelaySeconds ?: 0) as Integer
            if (newVal != policy.exportDelaySeconds) {
                drifted = true
                policy.exportDelaySeconds = newVal
            }
            if (!payload.containsKey('exportDelayDays')) {
                Integer derived = Math.max(0, Math.round(newVal / 86400.0))
                if (derived != policy.exportDelayDays) {
                    drifted = true
                    policy.exportDelayDays = derived
                }
            }
        }
        if (payload.containsKey('exportDelayDays')) {
            Integer newVal = (payload.exportDelayDays ?: 0) as Integer
            if (newVal != policy.exportDelayDays) {
                drifted = true
                policy.exportDelayDays = newVal
            }
        }
        if (payload.containsKey('visibilityEnabled')) {
            boolean newVal = toBoolean(payload.visibilityEnabled)
            if (newVal != policy.visibilityEnabled) {
                drifted = true
                policy.visibilityEnabled = newVal
            }
        }
        if (payload.containsKey('visibilityWls')) {
            List<String> values = normalizeVisibility(payload.visibilityWls)
            if (values != policy.visibilityWls) {
                drifted = true
                policy.visibilityWls = values
            }
        }
        if (payload.containsKey('policyRevision')) {
            String newVal = payload.policyRevision?.toString()
            if (newVal && newVal != policy.policyRevision) {
                drifted = true
                policy.policyRevision = newVal
            }
        }
        if (payload.containsKey('effectiveFrom')) {
            def eff = payload.effectiveFrom
            if (eff instanceof Date) {
                if (!eff.equals(policy.effectiveFrom)) {
                    drifted = true
                    policy.effectiveFrom = eff
                }
            } else if (eff instanceof CharSequence) {
                try {
                    Date parsed = Date.from(java.time.OffsetDateTime.parse(eff.toString()).toInstant())
                    if (!parsed.equals(policy.effectiveFrom)) {
                        drifted = true
                        policy.effectiveFrom = parsed
                    }
                } catch (Exception ignored) {
                    throw new IllegalArgumentException('effectiveFrom must be an ISO-8601 timestamp')
                }
            }
        }

        if (updatedBy) {
            policy.updatedBy = updatedBy.toString()
        }
        if (updatedSource) {
            policy.updatedSource = updatedSource.toString()
        }

        policy.save(failOnError: true, flush: true)
        whiteLabel.baselinePolicy = policy
        whiteLabel.save(failOnError: true, flush: true)
        if (drifted) {
            policyMetricsService?.recordPolicyDrift(whiteLabelId)
            savePolicyRevision(policy, whiteLabel, updatedBy, updatedSource)
        }
        return policy
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value
        }
        if (value == null) {
            return false
        }
        return value.toString().toLowerCase() in ['true', '1', 'yes', 'on']
    }

    private List<String> normalizeVisibility(Object value) {
        if (value instanceof Collection) {
            return (value as Collection).collect { it?.toString()?.trim() }.findAll { it }
        }
        if (value instanceof CharSequence) {
            return value.toString().split(',')
                    .collect { it.trim() }
                    .findAll { it }
        }
        []
    }

    private void savePolicyRevision(WhiteLabelPolicy policy, WhiteLabel whiteLabel, String updatedBy, String updatedSource) {
        Map snapshot = [
                whiteLabelId     : whiteLabel.id,
                importEnabled    : policy.importEnabled,
                exportEnabled    : policy.exportEnabled,
                exportDelaySeconds: policy.exportDelaySeconds,
                exportDelayDays  : policy.exportDelayDays,
                visibilityEnabled: policy.visibilityEnabled,
                visibilityWls    : policy.visibilityWls,
                policyRevision   : policy.policyRevision,
                effectiveFrom    : policy.effectiveFrom,
                updatedBy        : policy.updatedBy,
                updatedSource    : policy.updatedSource
        ]
        new WhiteLabelPolicyRevision(
                policy        : policy,
                whiteLabel    : whiteLabel,
                policyRevision: policy.policyRevision,
                payload       : JsonOutput.toJson(snapshot),
                updatedBy     : updatedBy,
                updatedSource : updatedSource,
                effectiveFrom : policy.effectiveFrom
        ).save(failOnError: true, flush: true)
    }
}

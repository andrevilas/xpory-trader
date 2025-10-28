package cpservice

import grails.converters.JSON
import grails.core.GrailsApplication
import org.springframework.http.HttpStatus

class PolicyAgentController {

    static responseFormats = ['json']

    WhiteLabelPolicyService whiteLabelPolicyService
    PolicyMetricsService policyMetricsService
    GrailsApplication grailsApplication
    TraderAccountService traderAccountService
    GovernanceSigningService governanceSigningService
    GovernanceTelemetryService governanceTelemetryService

    def pull() {
        Map payload = request.JSON as Map ?: [:]
        Collection<String> ids = (payload.whiteLabelIds ?: []) as Collection<String>
        Date since = null
        def sinceVal = payload.since
        if (sinceVal) {
            try {
                since = Date.from(java.time.OffsetDateTime.parse(sinceVal.toString()).toInstant())
            } catch (Exception ignored) {
                render status: HttpStatus.BAD_REQUEST.value(), contentType: "application/json", text: ([error: 'since must be ISO-8601 timestamp'] as JSON)
                return
            }
        }

        try {
            List<WhiteLabelPolicy> policies = policyMetricsService.timePolicyPull {
                whiteLabelPolicyService.listBaselines(ids ? ids : null, since)
            }
            Map<String, TraderAccount> traderMap = [:]
            if (policies) {
                Collection<String> wlIds = policies.collect { it.whiteLabel.id }.unique()
                traderMap = TraderAccount.findAll("from TraderAccount ta where ta.whiteLabel.id in (:ids)", [ids: wlIds])
                        .collectEntries { TraderAccount ta -> [(ta.whiteLabel.id): ta] }
            }
            def responseBody = [
                    generatedAt: new Date(),
                    count      : policies.size(),
                    items      : policies.collect { WhiteLabelPolicy policy ->
                        Map traderPayload = traderAccountService?.asPolicyPayload(traderMap[policy.whiteLabel.id])
                        [
                                whiteLabelId        : policy.whiteLabel.id,
                                white_label_id      : policy.whiteLabel.id,
                                policyId            : policy.id,
                                importEnabled       : policy.importEnabled,
                                exportEnabled       : policy.exportEnabled,
                                exportDelaySeconds  : policy.exportDelaySeconds,
                                exportDelayDays     : policy.exportDelayDays,
                                visibilityEnabled   : policy.visibilityEnabled,
                                visibilityWls       : policy.visibilityWls,
                                policyRevision      : policy.policyRevision,
                                effectiveFrom       : policy.effectiveFrom,
                                lastUpdated         : policy.lastUpdated,
                                traderAccount       : traderPayload,
                                import_enabled      : policy.importEnabled,
                                export_enabled      : policy.exportEnabled,
                                export_delay_days   : policy.exportDelayDays,
                                visibility_wls      : policy.visibilityWls
                        ]
                    }
            ]
            Map signature = governanceSigningService.signPayload(responseBody as Map)
            responseBody.signature = signature
            applyCacheHeaders()
            responseBody.items.each { Map item ->
                governanceTelemetryService.recordPolicyPackageSent(item.whiteLabelId?.toString(), [signature: signature.value])
            }
            render status: HttpStatus.OK.value(), contentType: "application/json", text: (responseBody as JSON)
        } catch (IllegalArgumentException ex) {
            render status: HttpStatus.BAD_REQUEST.value(), contentType: "application/json", text: ([error: ex.message] as JSON)
        }
    }

    private void applyCacheHeaders() {
        int ttl = grailsApplication?.config?.getProperty('app.policy.cacheTtlSeconds', Integer, 300)
        response.setHeader('Cache-Control', "private, max-age=${ttl}")
        response.setHeader('X-Policy-Cache-Ttl', ttl.toString())
    }
}

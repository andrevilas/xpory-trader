package cpservice

import grails.gorm.transactions.Transactional
import java.math.BigDecimal

@Transactional(readOnly = true)
class ReportService {

    Map tradeBalanceSummary() {
        List<Relationship> relationships = Relationship.list()
        BigDecimal totalLimit = relationships.inject(BigDecimal.ZERO) { acc, rel ->
            acc + (rel.limitAmount ?: BigDecimal.ZERO)
        }
        Map<String, List<Relationship>> byStatus = relationships.groupBy { it.status ?: 'unknown' }
        Map<String, Integer> statusCounts = byStatus.collectEntries { k, v -> [k, v.size()] }

        [
                generatedAt : new Date(),
                totals      : [
                        relationships: relationships.size(),
                        active       : statusCounts.get('active', 0),
                        blocked      : statusCounts.get('blocked', 0),
                        inactive     : statusCounts.get('inactive', 0),
                        totalLimit   : totalLimit
                ],
                relationships: relationships.collect { rel ->
                    [
                            sourceId   : rel.sourceId,
                            targetId   : rel.targetId,
                            fxRate     : rel.fxRate,
                            limitAmount: rel.limitAmount,
                            status     : rel.status,
                            updatedAt  : rel.lastUpdated
                    ]
                }
        ]
    }
}

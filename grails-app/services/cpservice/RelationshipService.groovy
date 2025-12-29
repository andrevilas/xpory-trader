package cpservice

import grails.gorm.transactions.Transactional
import java.math.BigDecimal

@Transactional
class RelationshipService {

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
}

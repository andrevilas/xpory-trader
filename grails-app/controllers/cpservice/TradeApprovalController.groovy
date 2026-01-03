package cpservice

import grails.converters.JSON
import org.springframework.http.HttpStatus

class TradeApprovalController {

    static responseFormats = ['json']

    TradeApprovalService tradeApprovalService

    def pending() {
        Map result = tradeApprovalService.listPending(params)
        render status: HttpStatus.OK.value(), contentType: 'application/json', text: (result as JSON)
    }

    def approve() {
        String tradeId = params.tradeId
        Map payload = request.JSON as Map ?: [:]
        try {
            Map result = tradeApprovalService.decide(tradeId, TradeApproval.DECISION_APPROVED, payload.reason?.toString(), resolveUser())
            render status: HttpStatus.OK.value(), contentType: 'application/json', text: ([
                    approval: approvalAsMap(result.approval as TradeApproval),
                    exporter: result.exporter
            ] as JSON)
        } catch (IllegalArgumentException ex) {
            render status: HttpStatus.BAD_REQUEST.value(), contentType: 'application/json', text: ([error: ex.message] as JSON)
        } catch (IllegalStateException ex) {
            render status: HttpStatus.CONFLICT.value(), contentType: 'application/json', text: ([error: ex.message] as JSON)
        }
    }

    def reject() {
        String tradeId = params.tradeId
        Map payload = request.JSON as Map ?: [:]
        try {
            Map result = tradeApprovalService.decide(tradeId, TradeApproval.DECISION_REJECTED, payload.reason?.toString(), resolveUser())
            render status: HttpStatus.OK.value(), contentType: 'application/json', text: ([
                    approval: approvalAsMap(result.approval as TradeApproval),
                    exporter: result.exporter
            ] as JSON)
        } catch (IllegalArgumentException ex) {
            render status: HttpStatus.BAD_REQUEST.value(), contentType: 'application/json', text: ([error: ex.message] as JSON)
        } catch (IllegalStateException ex) {
            render status: HttpStatus.CONFLICT.value(), contentType: 'application/json', text: ([error: ex.message] as JSON)
        }
    }

    private AdminUser resolveUser() {
        String userId = request.getAttribute('adminUserId')?.toString()
        String role = request.getAttribute('adminUserRole')?.toString()
        if (!userId) {
            return null
        }
        AdminUser user = AdminUser.get(userId)
        if (!user && role) {
            user = new AdminUser(id: userId, role: role)
        }
        return user
    }

    private static Map approvalAsMap(TradeApproval approval) {
        if (!approval) {
            return null
        }
        return [
                id: approval.id,
                tradeId: approval.tradeId,
                externalTradeId: approval.externalTradeId,
                originWhiteLabelId: approval.originWhiteLabelId,
                targetWhiteLabelId: approval.targetWhiteLabelId,
                decision: approval.decision,
                reason: approval.reason,
                statusBefore: approval.statusBefore,
                statusAfter: approval.statusAfter,
                decidedByUserId: approval.decidedByUserId,
                decidedByRole: approval.decidedByRole,
                createdAt: approval.dateCreated,
                updatedAt: approval.lastUpdated
        ]
    }
}

package cpservice

import groovy.transform.ToString

@ToString(includeNames = true, includes = ['id', 'tradeId', 'decision', 'originWhiteLabelId', 'targetWhiteLabelId'])
class TradeApproval {

    static final String DECISION_APPROVED = 'APPROVED'
    static final String DECISION_REJECTED = 'REJECTED'

    String id
    String tradeId
    String externalTradeId
    String originWhiteLabelId
    String targetWhiteLabelId
    String decision
    String reason
    Integer requestedQuantity
    BigDecimal unitPrice
    String currency = 'X'
    String statusBefore = 'PENDING'
    String statusAfter
    Integer exporterResponseCode
    String exporterResponseBody
    String decidedByUserId
    String decidedByRole

    Date dateCreated
    Date lastUpdated

    static mapping = {
        table 'cp_trade_approvals'
        id generator: 'uuid2', type: 'string', length: 36
        tradeId column: 'trade_id'
        externalTradeId column: 'external_trade_id'
        originWhiteLabelId column: 'origin_white_label_id'
        targetWhiteLabelId column: 'target_white_label_id'
        statusBefore column: 'status_before'
        statusAfter column: 'status_after'
        exporterResponseCode column: 'exporter_response_code'
        exporterResponseBody column: 'exporter_response_body', type: 'text'
        decidedByUserId column: 'decided_by_user_id'
        decidedByRole column: 'decided_by_role'
    }

    static constraints = {
        tradeId blank: false, maxSize: 120, unique: true
        externalTradeId nullable: true, maxSize: 120
        originWhiteLabelId blank: false, maxSize: 64
        targetWhiteLabelId blank: false, maxSize: 64
        decision blank: false, inList: [DECISION_APPROVED, DECISION_REJECTED]
        reason nullable: true, maxSize: 255
        requestedQuantity nullable: true, min: 0
        unitPrice nullable: true, scale: 2
        currency blank: false, maxSize: 10
        statusBefore nullable: true, maxSize: 32
        statusAfter nullable: true, maxSize: 32
        exporterResponseCode nullable: true
        exporterResponseBody nullable: true
        decidedByUserId nullable: true, maxSize: 36
        decidedByRole nullable: true, maxSize: 32
    }
}

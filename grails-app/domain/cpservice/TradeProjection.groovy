package cpservice

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode(includes = 'id')
@ToString(includes = ['id', 'tradeExternalId', 'status', 'settlementStatus'], includeNames = true)
class TradeProjection {

    String id
    String tradeExternalId
    String tradeId
    String originWhiteLabelId
    String targetWhiteLabelId
    String status
    String settlementStatus
    String eventName
    BigDecimal unitPrice
    Integer requestedQuantity
    Integer confirmedQuantity
    String currency = 'X'
    String idempotencyKey
    Date occurredAt
    Date confirmedAt
    Date settledAt
    Date refundedAt
    Date lastEventTimestamp

    Date dateCreated
    Date lastUpdated

    static mapping = {
        table 'cp_trades'
        id generator: 'uuid2', type: 'string', length: 36
        tradeExternalId column: 'trade_external_id'
        tradeId column: 'trade_id'
        originWhiteLabelId column: 'origin_white_label_id'
        targetWhiteLabelId column: 'target_white_label_id'
        settlementStatus column: 'settlement_status'
        eventName column: 'event_name'
        unitPrice column: 'unit_price', scale: 2
        requestedQuantity column: 'requested_quantity'
        confirmedQuantity column: 'confirmed_quantity'
        idempotencyKey column: 'idempotency_key'
        occurredAt column: 'occurred_at'
        confirmedAt column: 'confirmed_at'
        settledAt column: 'settled_at'
        refundedAt column: 'refunded_at'
        lastEventTimestamp column: 'last_event_timestamp'
    }

    static constraints = {
        tradeExternalId blank: false, maxSize: 120, unique: true
        tradeId nullable: true, maxSize: 120
        originWhiteLabelId nullable: true, maxSize: 36
        targetWhiteLabelId nullable: true, maxSize: 36
        status nullable: true, maxSize: 40
        settlementStatus nullable: true, maxSize: 40
        eventName nullable: true, maxSize: 60
        unitPrice nullable: true
        requestedQuantity nullable: true
        confirmedQuantity nullable: true
        currency blank: false, maxSize: 8
        idempotencyKey nullable: true, maxSize: 255
        occurredAt nullable: true
        confirmedAt nullable: true
        settledAt nullable: true
        refundedAt nullable: true
        lastEventTimestamp nullable: true
    }
}

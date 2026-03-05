databaseChangeLog = {
    changeSet(author: "codex", id: "20260305-trade-projection-01") {
        createTable(tableName: "cp_trades") {
            column(name: "id", type: "varchar(36)") {
                constraints(primaryKey: "true", primaryKeyName: "pk_cp_trades", nullable: "false")
            }
            column(name: "version", type: "bigint") {
                constraints(nullable: "false")
            }
            column(name: "trade_external_id", type: "varchar(120)") {
                constraints(nullable: "false")
            }
            column(name: "trade_id", type: "varchar(120)") {
                constraints(nullable: "true")
            }
            column(name: "origin_white_label_id", type: "varchar(36)") {
                constraints(nullable: "true")
            }
            column(name: "target_white_label_id", type: "varchar(36)") {
                constraints(nullable: "true")
            }
            column(name: "status", type: "varchar(40)") {
                constraints(nullable: "true")
            }
            column(name: "settlement_status", type: "varchar(40)") {
                constraints(nullable: "true")
            }
            column(name: "event_name", type: "varchar(60)") {
                constraints(nullable: "true")
            }
            column(name: "unit_price", type: "numeric(18,2)") {
                constraints(nullable: "true")
            }
            column(name: "requested_quantity", type: "integer") {
                constraints(nullable: "true")
            }
            column(name: "confirmed_quantity", type: "integer") {
                constraints(nullable: "true")
            }
            column(name: "currency", type: "varchar(8)") {
                constraints(nullable: "false")
            }
            column(name: "idempotency_key", type: "varchar(255)") {
                constraints(nullable: "true")
            }
            column(name: "occurred_at", type: "timestamp") {
                constraints(nullable: "true")
            }
            column(name: "confirmed_at", type: "timestamp") {
                constraints(nullable: "true")
            }
            column(name: "settled_at", type: "timestamp") {
                constraints(nullable: "true")
            }
            column(name: "refunded_at", type: "timestamp") {
                constraints(nullable: "true")
            }
            column(name: "last_event_timestamp", type: "timestamp") {
                constraints(nullable: "true")
            }
            column(name: "date_created", type: "timestamp") {
                constraints(nullable: "false")
            }
            column(name: "last_updated", type: "timestamp") {
                constraints(nullable: "false")
            }
        }

        addUniqueConstraint(columnNames: "trade_external_id", tableName: "cp_trades", constraintName: "uk_cp_trades_external_id")
        createIndex(indexName: "idx_cp_trades_pair", tableName: "cp_trades") {
            column(name: "origin_white_label_id")
            column(name: "target_white_label_id")
        }
        createIndex(indexName: "idx_cp_trades_status", tableName: "cp_trades") {
            column(name: "status")
            column(name: "settlement_status")
        }
        createIndex(indexName: "idx_cp_trades_occurred", tableName: "cp_trades") {
            column(name: "occurred_at")
        }
    }
}

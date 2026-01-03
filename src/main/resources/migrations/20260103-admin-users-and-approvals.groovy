databaseChangeLog = {
    changeSet(author: "codex", id: "20260103-admin-users-and-approvals") {
        createTable(tableName: "cp_users") {
            column(name: "id", type: "varchar(36)") {
                constraints(primaryKey: true, nullable: false)
            }
            column(name: "email", type: "varchar(200)") {
                constraints(nullable: false, unique: true)
            }
            column(name: "password_hash", type: "varchar(255)") {
                constraints(nullable: false)
            }
            column(name: "role", type: "varchar(32)") {
                constraints(nullable: false)
            }
            column(name: "status", type: "varchar(32)") {
                constraints(nullable: false)
            }
            column(name: "last_login_at", type: "timestamp")
            column(name: "date_created", type: "timestamp")
            column(name: "last_updated", type: "timestamp")
        }

        createTable(tableName: "cp_trade_approvals") {
            column(name: "id", type: "varchar(36)") {
                constraints(primaryKey: true, nullable: false)
            }
            column(name: "trade_id", type: "varchar(120)") {
                constraints(nullable: false, unique: true)
            }
            column(name: "external_trade_id", type: "varchar(120)")
            column(name: "origin_white_label_id", type: "varchar(64)") {
                constraints(nullable: false)
            }
            column(name: "target_white_label_id", type: "varchar(64)") {
                constraints(nullable: false)
            }
            column(name: "decision", type: "varchar(32)") {
                constraints(nullable: false)
            }
            column(name: "reason", type: "varchar(255)")
            column(name: "requested_quantity", type: "integer")
            column(name: "unit_price", type: "decimal(19,2)")
            column(name: "currency", type: "varchar(10)")
            column(name: "status_before", type: "varchar(32)")
            column(name: "status_after", type: "varchar(32)")
            column(name: "exporter_response_code", type: "integer")
            column(name: "exporter_response_body", type: "text")
            column(name: "decided_by_user_id", type: "varchar(36)")
            column(name: "decided_by_role", type: "varchar(32)")
            column(name: "date_created", type: "timestamp")
            column(name: "last_updated", type: "timestamp")
        }

        createIndex(tableName: "cp_trade_approvals", indexName: "idx_cp_trade_approvals_origin") {
            column(name: "origin_white_label_id")
        }
        createIndex(tableName: "cp_trade_approvals", indexName: "idx_cp_trade_approvals_target") {
            column(name: "target_white_label_id")
        }
        createIndex(tableName: "cp_trade_approvals", indexName: "idx_cp_trade_approvals_created") {
            column(name: "date_created")
        }
    }
}

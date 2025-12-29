databaseChangeLog = {

    changeSet(author: "codex", id: "20251227-01-add-gateway-url") {
        addColumn(tableName: "cp_white_labels") {
            column(name: "gateway_url", type: "varchar(500)")
        }
    }

    changeSet(author: "codex", id: "20251227-02-imbalance-dispatch-status") {
        addColumn(tableName: "cp_imbalance_signals") {
            column(name: "dispatch_status", type: "varchar(30)", defaultValue: "pending") {
                constraints(nullable: "false")
            }
            column(name: "dispatch_attempts", type: "integer", defaultValueNumeric: "0") {
                constraints(nullable: "false")
            }
            column(name: "dispatched_at", type: "timestamp")
            column(name: "last_dispatch_at", type: "timestamp")
            column(name: "dispatch_error", type: "varchar(500)")
        }
    }
}


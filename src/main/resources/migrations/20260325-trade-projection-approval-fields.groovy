databaseChangeLog = {
    changeSet(author: "codex", id: "20260325-trade-projection-approval-fields-01") {
        addColumn(tableName: "cp_trades") {
            column(name: "approval_mode", type: "varchar(20)")
            column(name: "approval_path", type: "varchar(20)")
            column(name: "pending_reason", type: "varchar(120)")
        }
    }
}

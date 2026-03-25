databaseChangeLog = {
    changeSet(author: "codex", id: "20260325-01-relationship-approval-policy") {
        addColumn(tableName: "cp_relationships") {
            column(name: "approval_mode", type: "varchar(20)", defaultValue: "MANUAL") {
                constraints(nullable: false)
            }
            column(name: "manual_approval_above_amount", type: "numeric(18,2)")
            column(name: "manual_approval_above_qty", type: "integer")
            column(name: "manual_approval_on_first_trade", type: "boolean", defaultValueBoolean: false) {
                constraints(nullable: false)
            }
            column(name: "manual_approval_on_imbalance", type: "boolean", defaultValueBoolean: false) {
                constraints(nullable: false)
            }
        }
    }
}

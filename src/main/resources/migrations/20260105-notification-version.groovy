databaseChangeLog = {
    changeSet(author: "codex", id: "20260105-notification-version") {
        addColumn(tableName: "cp_notifications") {
            column(name: "version", type: "integer", defaultValueNumeric: 0) {
                constraints(nullable: false)
            }
        }
        addColumn(tableName: "cp_notification_recipients") {
            column(name: "version", type: "integer", defaultValueNumeric: 0) {
                constraints(nullable: false)
            }
        }
    }
}

databaseChangeLog = {
    changeSet(author: "codex", id: "20251230-telemetry-last-updated") {
        addColumn(tableName: "cp_telemetry") {
            column(name: "last_updated", type: "timestamp")
        }
    }
}

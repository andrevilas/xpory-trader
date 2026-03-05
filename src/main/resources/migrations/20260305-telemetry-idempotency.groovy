databaseChangeLog = {
    changeSet(author: "codex", id: "20260305-telemetry-idempotency-01") {
        addColumn(tableName: "cp_telemetry") {
            column(name: "idempotency_key", type: "varchar(255)") {
                constraints(nullable: "true")
            }
            column(name: "dedupe_fingerprint", type: "varchar(64)") {
                constraints(nullable: "true")
            }
        }

        createIndex(indexName: "idx_cp_telemetry_idempotency_key", tableName: "cp_telemetry") {
            column(name: "idempotency_key")
        }

        createIndex(indexName: "idx_cp_telemetry_dedupe_fingerprint", tableName: "cp_telemetry", unique: true) {
            column(name: "dedupe_fingerprint")
        }
    }
}

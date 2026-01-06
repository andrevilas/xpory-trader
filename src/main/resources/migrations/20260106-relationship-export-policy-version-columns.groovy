databaseChangeLog = {
    changeSet(author: "codex", id: "20260106-04-offer-category-snapshot-version") {
        addColumn(tableName: "cp_offer_category_snapshots") {
            column(name: "version", type: "bigint", defaultValueNumeric: 0) {
                constraints(nullable: false)
            }
        }
    }

    changeSet(author: "codex", id: "20260106-05-offer-entity-snapshot-version") {
        addColumn(tableName: "cp_entity_snapshots") {
            column(name: "version", type: "bigint", defaultValueNumeric: 0) {
                constraints(nullable: false)
            }
        }
    }
}

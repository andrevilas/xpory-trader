databaseChangeLog = {
    changeSet(author: "codex", id: "20260106-01-relationship-export-policy") {
        addColumn(tableName: "cp_relationships") {
            column(name: "export_policy", type: "text")
        }
    }

    changeSet(author: "codex", id: "20260106-02-offer-category-snapshots") {
        createTable(tableName: "cp_offer_category_snapshots") {
            column(name: "id", type: "varchar(36)") {
                constraints(primaryKey: true, nullable: false)
            }
            column(name: "exporter_wl_id", type: "varchar(36)") {
                constraints(nullable: false)
            }
            column(name: "category_id", type: "varchar(120)") {
                constraints(nullable: false)
            }
            column(name: "category_name", type: "varchar(200)")
            column(name: "active_count", type: "integer") {
                constraints(nullable: false)
            }
            column(name: "collected_at", type: "timestamp") {
                constraints(nullable: false)
            }
            column(name: "date_created", type: "timestamp")
            column(name: "last_updated", type: "timestamp")
        }

        addUniqueConstraint(tableName: "cp_offer_category_snapshots", columnNames: "exporter_wl_id,category_id", constraintName: "uk_cp_cat_snapshot")
        createIndex(tableName: "cp_offer_category_snapshots", indexName: "idx_cp_cat_snapshot_exporter") {
            column(name: "exporter_wl_id")
        }
    }

    changeSet(author: "codex", id: "20260106-03-offer-entity-snapshots") {
        createTable(tableName: "cp_entity_snapshots") {
            column(name: "id", type: "varchar(36)") {
                constraints(primaryKey: true, nullable: false)
            }
            column(name: "exporter_wl_id", type: "varchar(36)") {
                constraints(nullable: false)
            }
            column(name: "entity_id", type: "varchar(120)") {
                constraints(nullable: false)
            }
            column(name: "name", type: "varchar(200)")
            column(name: "active_offers_count", type: "integer") {
                constraints(nullable: false)
            }
            column(name: "status", type: "varchar(40)")
            column(name: "updated_at", type: "timestamp")
            column(name: "collected_at", type: "timestamp") {
                constraints(nullable: false)
            }
            column(name: "date_created", type: "timestamp")
            column(name: "last_updated", type: "timestamp")
        }

        addUniqueConstraint(tableName: "cp_entity_snapshots", columnNames: "exporter_wl_id,entity_id", constraintName: "uk_cp_entity_snapshot")
        createIndex(tableName: "cp_entity_snapshots", indexName: "idx_cp_entity_snapshot_exporter") {
            column(name: "exporter_wl_id")
        }
    }
}

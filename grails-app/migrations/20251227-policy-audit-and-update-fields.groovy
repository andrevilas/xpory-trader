databaseChangeLog = {

    changeSet(author: "codex", id: "20251227-03-policy-update-fields") {
        addColumn(tableName: "cp_white_label_policies") {
            column(name: "updated_by", type: "varchar(120)")
            column(name: "updated_source", type: "varchar(120)")
        }
    }

    changeSet(author: "codex", id: "20251227-04-relationship-update-fields") {
        addColumn(tableName: "cp_relationships") {
            column(name: "updated_by", type: "varchar(120)")
            column(name: "updated_source", type: "varchar(120)")
        }
    }

    changeSet(author: "codex", id: "20251227-05-imbalance-update-fields") {
        addColumn(tableName: "cp_imbalance_signals") {
            column(name: "updated_by", type: "varchar(120)")
            column(name: "updated_source", type: "varchar(120)")
        }
    }

    changeSet(author: "codex", id: "20251227-06-policy-revision-table") {
        createTable(tableName: "cp_white_label_policy_revisions") {
            column(name: "id", type: "varchar(36)") {
                constraints(primaryKey: "true", primaryKeyName: "pk_cp_policy_revisions", nullable: "false")
            }
            column(name: "version", type: "bigint") {
                constraints(nullable: "false")
            }
            column(name: "white_label_id", type: "varchar(36)") {
                constraints(nullable: "false")
            }
            column(name: "policy_id", type: "varchar(36)") {
                constraints(nullable: "false")
            }
            column(name: "policy_revision", type: "varchar(50)") {
                constraints(nullable: "false")
            }
            column(name: "payload", type: "text") {
                constraints(nullable: "false")
            }
            column(name: "updated_by", type: "varchar(120)")
            column(name: "updated_source", type: "varchar(120)")
            column(name: "effective_from", type: "timestamp")
            column(name: "date_created", type: "timestamp") {
                constraints(nullable: "false")
            }
        }

        addForeignKeyConstraint(
                baseColumnNames: "white_label_id",
                baseTableName: "cp_white_label_policy_revisions",
                constraintName: "fk_cp_policy_rev_wl",
                referencedColumnNames: "id",
                referencedTableName: "cp_white_labels",
                onDelete: "CASCADE"
        )
        addForeignKeyConstraint(
                baseColumnNames: "policy_id",
                baseTableName: "cp_white_label_policy_revisions",
                constraintName: "fk_cp_policy_rev_policy",
                referencedColumnNames: "id",
                referencedTableName: "cp_white_label_policies",
                onDelete: "CASCADE"
        )
        createIndex(indexName: "idx_cp_policy_rev_wl", tableName: "cp_white_label_policy_revisions") {
            column(name: "white_label_id")
        }
    }
}


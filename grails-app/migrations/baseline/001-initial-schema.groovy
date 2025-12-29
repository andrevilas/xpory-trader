databaseChangeLog = {

    changeSet(author: "codex", id: "20250211-01-create-white-labels") {
        createTable(tableName: "cp_white_labels") {
            column(name: "id", type: "varchar(36)") {
                constraints(primaryKey: "true", primaryKeyName: "pk_cp_white_labels", nullable: "false")
            }
            column(name: "version", type: "bigint") {
                constraints(nullable: "false")
            }
            column(name: "name", type: "varchar(120)") {
                constraints(nullable: "false")
            }
            column(name: "description", type: "varchar(500)") {
                constraints(nullable: "true")
            }
            column(name: "contact_email", type: "varchar(150)") {
                constraints(nullable: "false")
            }
            column(name: "status", type: "varchar(20)", defaultValue: "active") {
                constraints(nullable: "false")
            }
            column(name: "date_created", type: "timestamp") {
                constraints(nullable: "false")
            }
            column(name: "last_updated", type: "timestamp") {
                constraints(nullable: "false")
            }
        }

        addUniqueConstraint(columnNames: "name", constraintName: "uk_cp_white_labels_name", tableName: "cp_white_labels")
        createIndex(indexName: "idx_cp_white_labels_status", tableName: "cp_white_labels") {
            column(name: "status")
        }
    }

    changeSet(author: "codex", id: "20250301-01-create-trader-accounts") {
        createTable(tableName: "cp_trader_accounts") {
            column(name: "id", type: "varchar(36)") {
                constraints(primaryKey: "true", primaryKeyName: "pk_cp_trader_accounts", nullable: "false")
            }
            column(name: "version", type: "bigint") {
                constraints(nullable: "false")
            }
            column(name: "white_label_id", type: "varchar(36)") {
                constraints(nullable: "false")
            }
            column(name: "name", type: "varchar(120)") {
                constraints(nullable: "false")
            }
            column(name: "status", type: "varchar(20)", defaultValue: "active") {
                constraints(nullable: "false")
            }
            column(name: "contact_email", type: "varchar(150)") {
                constraints(nullable: "true")
            }
            column(name: "contact_phone", type: "varchar(60)") {
                constraints(nullable: "true")
            }
            column(name: "created_by_admin", type: "varchar(120)") {
                constraints(nullable: "true")
            }
            column(name: "issued_at", type: "timestamp") {
                constraints(nullable: "false")
            }
            column(name: "confirmed_at", type: "timestamp") {
                constraints(nullable: "true")
            }
            column(name: "date_created", type: "timestamp") {
                constraints(nullable: "false")
            }
            column(name: "last_updated", type: "timestamp") {
                constraints(nullable: "false")
            }
        }

        addForeignKeyConstraint(
                baseColumnNames: "white_label_id",
                baseTableName: "cp_trader_accounts",
                constraintName: "fk_cp_trader_account_wl",
                referencedColumnNames: "id",
                referencedTableName: "cp_white_labels",
                onDelete: "CASCADE"
        )

        addUniqueConstraint(columnNames: "white_label_id", constraintName: "uk_cp_trader_accounts_wl", tableName: "cp_trader_accounts")
        createIndex(indexName: "idx_cp_trader_status", tableName: "cp_trader_accounts") {
            column(name: "status")
        }
    }

    changeSet(author: "codex", id: "20250211-02-create-white-label-policies") {
        createTable(tableName: "cp_white_label_policies") {
            column(name: "id", type: "varchar(36)") {
                constraints(primaryKey: "true", primaryKeyName: "pk_cp_white_label_policies", nullable: "false")
            }
            column(name: "version", type: "bigint") {
                constraints(nullable: "false")
            }
            column(name: "white_label_id", type: "varchar(36)") {
                constraints(nullable: "false")
            }
            column(name: "import_enabled", type: "boolean", defaultValueBoolean: "false") {
                constraints(nullable: "false")
            }
            column(name: "export_enabled", type: "boolean", defaultValueBoolean: "false") {
                constraints(nullable: "false")
            }
            column(name: "export_delay_seconds", type: "integer", defaultValueNumeric: "0") {
                constraints(nullable: "false")
            }
            column(name: "visibility_enabled", type: "boolean", defaultValueBoolean: "false") {
                constraints(nullable: "false")
            }
            column(name: "policy_revision", type: "varchar(50)", defaultValue: "baseline") {
                constraints(nullable: "false")
            }
            column(name: "effective_from", type: "timestamp") {
                constraints(nullable: "false")
            }
            column(name: "date_created", type: "timestamp") {
                constraints(nullable: "false")
            }
            column(name: "last_updated", type: "timestamp") {
                constraints(nullable: "false")
            }
        }

        addUniqueConstraint(columnNames: "white_label_id", constraintName: "uk_cp_white_label_policies_wl", tableName: "cp_white_label_policies")

        addForeignKeyConstraint(
                baseColumnNames: "white_label_id",
                baseTableName: "cp_white_label_policies",
                constraintName: "fk_cp_wl_policy_wl",
                referencedColumnNames: "id",
                referencedTableName: "cp_white_labels",
                onDelete: "CASCADE"
        )
    }

    changeSet(author: "codex", id: "20250302-01-extend-policies") {
        addColumn(tableName: "cp_white_label_policies") {
            column(name: "export_delay_days", type: "integer", defaultValueNumeric: "0") {
                constraints(nullable: "false")
            }
            column(name: "visibility_wls", type: "text")
        }
        grailsChange {
            change {
                sql.execute("UPDATE cp_white_label_policies SET visibility_wls = '[]' WHERE visibility_wls IS NULL")
            }
        }
        addNotNullConstraint(tableName: "cp_white_label_policies", columnName: "visibility_wls", columnDataType: "text")
    }

    changeSet(author: "codex", id: "20250211-03-create-telemetry") {
        createTable(tableName: "cp_telemetry") {
            column(name: "id", type: "varchar(36)") {
                constraints(primaryKey: "true", primaryKeyName: "pk_cp_telemetry", nullable: "false")
            }
            column(name: "version", type: "bigint") {
                constraints(nullable: "false")
            }
            column(name: "white_label_id", type: "varchar(36)") {
                constraints(nullable: "false")
            }
            column(name: "node_id", type: "varchar(100)") {
                constraints(nullable: "false")
            }
            column(name: "event_type", type: "varchar(120)") {
                constraints(nullable: "false")
            }
            column(name: "payload", type: "TEXT") {
                constraints(nullable: "false")
            }
            column(name: "event_timestamp", type: "timestamp") {
                constraints(nullable: "false")
            }
            column(name: "date_created", type: "timestamp") {
                constraints(nullable: "false")
            }
        }

        addForeignKeyConstraint(
                baseColumnNames: "white_label_id",
                baseTableName: "cp_telemetry",
                constraintName: "fk_cp_telemetry_wl",
                referencedColumnNames: "id",
                referencedTableName: "cp_white_labels",
                onDelete: "CASCADE"
        )

        createIndex(indexName: "idx_cp_telemetry_wl_ts", tableName: "cp_telemetry") {
            column(name: "white_label_id")
            column(name: "event_timestamp")
        }
        createIndex(indexName: "idx_cp_telemetry_event_type", tableName: "cp_telemetry") {
            column(name: "event_type")
        }
        createIndex(indexName: "idx_cp_telemetry_node", tableName: "cp_telemetry") {
            column(name: "node_id")
        }
    }

    changeSet(author: "codex", id: "20251010-01-create-relationships") {
        createTable(tableName: "cp_relationships") {
            column(name: "id", type: "varchar(36)") {
                constraints(primaryKey: "true", primaryKeyName: "pk_cp_relationships", nullable: "false")
            }
            column(name: "version", type: "bigint") {
                constraints(nullable: "false")
            }
            column(name: "source_id", type: "varchar(36)") {
                constraints(nullable: "false")
            }
            column(name: "target_id", type: "varchar(36)") {
                constraints(nullable: "false")
            }
            column(name: "fx_rate", type: "numeric(18,6)") {
                constraints(nullable: "false")
            }
            column(name: "limit_amount", type: "numeric(18,2)") {
                constraints(nullable: "false")
            }
            column(name: "status", type: "varchar(20)") {
                constraints(nullable: "false")
            }
            column(name: "notes", type: "varchar(500)") {
                constraints(nullable: "true")
            }
            column(name: "date_created", type: "timestamp") {
                constraints(nullable: "false")
            }
            column(name: "last_updated", type: "timestamp") {
                constraints(nullable: "false")
            }
        }

        addUniqueConstraint(columnNames: "source_id,target_id", constraintName: "uk_cp_relationship_pair", tableName: "cp_relationships")
        createIndex(indexName: "idx_cp_relationship_src", tableName: "cp_relationships") {
            column(name: "source_id")
        }
        createIndex(indexName: "idx_cp_relationship_dst", tableName: "cp_relationships") {
            column(name: "target_id")
        }
    }

    changeSet(author: "codex", id: "20251010-02-create-imbalance-signal") {
        createTable(tableName: "cp_imbalance_signals") {
            column(name: "id", type: "varchar(36)") {
                constraints(primaryKey: "true", primaryKeyName: "pk_cp_imbalance_signals", nullable: "false")
            }
            column(name: "version", type: "bigint") {
                constraints(nullable: "false")
            }
            column(name: "source_id", type: "varchar(36)") {
                constraints(nullable: "false")
            }
            column(name: "target_id", type: "varchar(36)") {
                constraints(nullable: "false")
            }
            column(name: "action", type: "varchar(20)") {
                constraints(nullable: "false")
            }
            column(name: "reason", type: "varchar(500)") {
                constraints(nullable: "true")
            }
            column(name: "initiated_by", type: "varchar(120)") {
                constraints(nullable: "true")
            }
            column(name: "effective_from", type: "timestamp") {
                constraints(nullable: "false")
            }
            column(name: "effective_until", type: "timestamp") {
                constraints(nullable: "true")
            }
            column(name: "acknowledged", type: "boolean", defaultValueBoolean: "false") {
                constraints(nullable: "false")
            }
            column(name: "acknowledged_by", type: "varchar(120)") {
                constraints(nullable: "true")
            }
            column(name: "acknowledged_at", type: "timestamp") {
                constraints(nullable: "true")
            }
            column(name: "date_created", type: "timestamp") {
                constraints(nullable: "false")
            }
            column(name: "last_updated", type: "timestamp") {
                constraints(nullable: "false")
            }
        }

        createIndex(indexName: "idx_cp_imbalance_src", tableName: "cp_imbalance_signals") {
            column(name: "source_id")
        }
        createIndex(indexName: "idx_cp_imbalance_dst", tableName: "cp_imbalance_signals") {
            column(name: "target_id")
        }
    }

    changeSet(author: "codex", id: "20251010-03-create-wl-keys") {
        createTable(tableName: "cp_white_label_keys") {
            column(name: "id", type: "varchar(36)") {
                constraints(primaryKey: "true", primaryKeyName: "pk_cp_white_label_keys", nullable: "false")
            }
            column(name: "version", type: "bigint") {
                constraints(nullable: "false")
            }
            column(name: "white_label_id", type: "varchar(36)") {
                constraints(nullable: "false")
            }
            column(name: "key_id", type: "varchar(80)") {
                constraints(nullable: "false")
            }
            column(name: "algorithm", type: "varchar(20)") {
                constraints(nullable: "false")
            }
            column(name: "public_key", type: "text") {
                constraints(nullable: "false")
            }
            column(name: "private_key", type: "text") {
                constraints(nullable: "false")
            }
            column(name: "active", type: "boolean") {
                constraints(nullable: "false")
            }
            column(name: "valid_from", type: "timestamp") {
                constraints(nullable: "false")
            }
            column(name: "valid_until", type: "timestamp") {
                constraints(nullable: "true")
            }
            column(name: "date_created", type: "timestamp") {
                constraints(nullable: "false")
            }
            column(name: "last_updated", type: "timestamp") {
                constraints(nullable: "false")
            }
        }

        addForeignKeyConstraint(
                baseColumnNames: "white_label_id",
                baseTableName: "cp_white_label_keys",
                constraintName: "fk_cp_wl_keys_wl",
                referencedColumnNames: "id",
                referencedTableName: "cp_white_labels",
                onDelete: "CASCADE"
        )

        addUniqueConstraint(columnNames: "key_id", constraintName: "uk_cp_wl_keys_kid", tableName: "cp_white_label_keys")
        createIndex(indexName: "idx_cp_wl_keys_wl", tableName: "cp_white_label_keys") {
            column(name: "white_label_id")
        }
    }
}

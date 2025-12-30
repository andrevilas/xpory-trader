databaseChangeLog = {

    changeSet(author: "codex", id: "20251230-01-wl-id-cascade") {
        dropForeignKeyConstraint(baseTableName: "cp_trader_accounts", constraintName: "fk_cp_trader_account_wl")
        addForeignKeyConstraint(
                baseColumnNames: "white_label_id",
                baseTableName: "cp_trader_accounts",
                constraintName: "fk_cp_trader_account_wl",
                referencedColumnNames: "id",
                referencedTableName: "cp_white_labels",
                onDelete: "CASCADE",
                onUpdate: "CASCADE"
        )

        dropForeignKeyConstraint(baseTableName: "cp_white_label_policies", constraintName: "fk_cp_wl_policy_wl")
        addForeignKeyConstraint(
                baseColumnNames: "white_label_id",
                baseTableName: "cp_white_label_policies",
                constraintName: "fk_cp_wl_policy_wl",
                referencedColumnNames: "id",
                referencedTableName: "cp_white_labels",
                onDelete: "CASCADE",
                onUpdate: "CASCADE"
        )

        dropForeignKeyConstraint(baseTableName: "cp_telemetry", constraintName: "fk_cp_telemetry_wl")
        addForeignKeyConstraint(
                baseColumnNames: "white_label_id",
                baseTableName: "cp_telemetry",
                constraintName: "fk_cp_telemetry_wl",
                referencedColumnNames: "id",
                referencedTableName: "cp_white_labels",
                onDelete: "CASCADE",
                onUpdate: "CASCADE"
        )

        dropForeignKeyConstraint(baseTableName: "cp_white_label_keys", constraintName: "fk_cp_wl_keys_wl")
        addForeignKeyConstraint(
                baseColumnNames: "white_label_id",
                baseTableName: "cp_white_label_keys",
                constraintName: "fk_cp_wl_keys_wl",
                referencedColumnNames: "id",
                referencedTableName: "cp_white_labels",
                onDelete: "CASCADE",
                onUpdate: "CASCADE"
        )

        dropForeignKeyConstraint(baseTableName: "cp_white_label_policy_revisions", constraintName: "fk_cp_policy_rev_wl")
        addForeignKeyConstraint(
                baseColumnNames: "white_label_id",
                baseTableName: "cp_white_label_policy_revisions",
                constraintName: "fk_cp_policy_rev_wl",
                referencedColumnNames: "id",
                referencedTableName: "cp_white_labels",
                onDelete: "CASCADE",
                onUpdate: "CASCADE"
        )
    }
}

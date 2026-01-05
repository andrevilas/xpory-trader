databaseChangeLog = {
    changeSet(author: "codex", id: "20260105-notifications") {
        createTable(tableName: "cp_notifications") {
            column(name: "id", type: "varchar(36)") {
                constraints(primaryKey: true, nullable: false)
            }
            column(name: "type", type: "varchar(64)") {
                constraints(nullable: false)
            }
            column(name: "title", type: "varchar(255)") {
                constraints(nullable: false)
            }
            column(name: "message", type: "text") {
                constraints(nullable: false)
            }
            column(name: "action_type", type: "varchar(80)")
            column(name: "action_payload", type: "text")
            column(name: "trade_id", type: "varchar(120)")
            column(name: "origin_white_label_id", type: "varchar(64)")
            column(name: "target_white_label_id", type: "varchar(64)")
            column(name: "date_created", type: "timestamp")
            column(name: "last_updated", type: "timestamp")
        }

        createTable(tableName: "cp_notification_recipients") {
            column(name: "id", type: "varchar(36)") {
                constraints(primaryKey: true, nullable: false)
            }
            column(name: "notification_id", type: "varchar(36)") {
                constraints(nullable: false)
            }
            column(name: "user_id", type: "varchar(36)") {
                constraints(nullable: false)
            }
            column(name: "read_at", type: "timestamp")
            column(name: "delivered_at", type: "timestamp")
            column(name: "date_created", type: "timestamp")
            column(name: "last_updated", type: "timestamp")
        }

        createIndex(tableName: "cp_notifications", indexName: "idx_cp_notifications_origin") {
            column(name: "origin_white_label_id")
        }
        createIndex(tableName: "cp_notifications", indexName: "idx_cp_notifications_target") {
            column(name: "target_white_label_id")
        }
        createIndex(tableName: "cp_notifications", indexName: "idx_cp_notifications_trade") {
            column(name: "trade_id")
        }
        createIndex(tableName: "cp_notification_recipients", indexName: "idx_cp_notification_recipient_notification") {
            column(name: "notification_id")
        }
        createIndex(tableName: "cp_notification_recipients", indexName: "idx_cp_notification_recipient_user") {
            column(name: "user_id")
        }
        createIndex(tableName: "cp_notification_recipients", indexName: "idx_cp_notification_recipient_unique") {
            column(name: "notification_id")
            column(name: "user_id")
        }
    }
}

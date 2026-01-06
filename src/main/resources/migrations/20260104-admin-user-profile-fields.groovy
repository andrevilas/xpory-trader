databaseChangeLog = {
    changeSet(author: "codex", id: "20260104-admin-user-profile-fields") {
        preConditions(onFail: "MARK_RAN") {
            not {
                columnExists(tableName: "cp_users", columnName: "name")
            }
            not {
                columnExists(tableName: "cp_users", columnName: "phone")
            }
        }
        addColumn(tableName: "cp_users") {
            column(name: "name", type: "varchar(200)")
            column(name: "phone", type: "varchar(40)")
        }
    }
}

databaseChangeLog = {
    changeSet(author: "codex", id: "20260104-admin-user-profile-fields") {
        addColumn(tableName: "cp_users") {
            column(name: "name", type: "varchar(200)")
            column(name: "phone", type: "varchar(40)")
        }
    }
}

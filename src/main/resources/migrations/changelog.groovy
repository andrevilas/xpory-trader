databaseChangeLog = {
    include file: 'baseline/001-initial-schema.groovy', relativeToChangelogFile: true
    include file: '20251227-imbalance-dispatch-and-gateway.groovy', relativeToChangelogFile: true
    include file: '20251227-policy-audit-and-update-fields.groovy', relativeToChangelogFile: true
    include file: '20251230-wl-id-update-cascade.groovy', relativeToChangelogFile: true
}

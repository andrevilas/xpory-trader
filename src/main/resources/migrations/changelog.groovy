databaseChangeLog = {
    include file: 'baseline/001-initial-schema.groovy', relativeToChangelogFile: true
    include file: '20251227-imbalance-dispatch-and-gateway.groovy', relativeToChangelogFile: true
    include file: '20251227-policy-audit-and-update-fields.groovy', relativeToChangelogFile: true
    include file: '20251230-wl-id-update-cascade.groovy', relativeToChangelogFile: true
    include file: '20251230-telemetry-last-updated.groovy', relativeToChangelogFile: true
    include file: '20260103-admin-users-and-approvals.groovy', relativeToChangelogFile: true
    include file: '20260105-notifications.groovy', relativeToChangelogFile: true
    include file: '20260105-notification-version.groovy', relativeToChangelogFile: true
}

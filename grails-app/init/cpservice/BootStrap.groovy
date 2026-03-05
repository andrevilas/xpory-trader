package cpservice

class BootStrap {

    CertificateRotationService certificateRotationService
    AdminUserService adminUserService
    ExportMetadataSyncSchedulerService exportMetadataSyncSchedulerService
    TradeReconciliationSchedulerService tradeReconciliationSchedulerService

    def init = { servletContext ->
        certificateRotationService?.start()
        exportMetadataSyncSchedulerService?.start()
        tradeReconciliationSchedulerService?.start()
        adminUserService?.bootstrapUsersIfNeeded()
    }
    def destroy = {
        certificateRotationService?.stop()
        exportMetadataSyncSchedulerService?.stop()
        tradeReconciliationSchedulerService?.stop()
    }
}

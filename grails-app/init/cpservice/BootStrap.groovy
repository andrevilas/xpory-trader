package cpservice

class BootStrap {

    CertificateRotationService certificateRotationService
    AdminUserService adminUserService
    ExportMetadataSyncSchedulerService exportMetadataSyncSchedulerService

    def init = { servletContext ->
        certificateRotationService?.start()
        exportMetadataSyncSchedulerService?.start()
        adminUserService?.bootstrapUsersIfNeeded()
    }
    def destroy = {
        certificateRotationService?.stop()
        exportMetadataSyncSchedulerService?.stop()
    }
}

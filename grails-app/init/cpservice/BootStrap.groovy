package cpservice

class BootStrap {

    CertificateRotationService certificateRotationService
    AdminUserService adminUserService

    def init = { servletContext ->
        certificateRotationService?.start()
        adminUserService?.bootstrapUsersIfNeeded()
    }
    def destroy = {
        certificateRotationService?.stop()
    }
}

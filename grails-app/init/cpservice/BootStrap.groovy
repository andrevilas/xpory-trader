package cpservice

class BootStrap {

    CertificateRotationService certificateRotationService

    def init = { servletContext ->
        certificateRotationService?.start()
    }
    def destroy = {
        certificateRotationService?.stop()
    }
}

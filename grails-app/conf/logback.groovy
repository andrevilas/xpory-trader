import grails.util.BuildSettings
import grails.util.Environment
import org.springframework.boot.logging.logback.ColorConverter
import org.springframework.boot.logging.logback.WhitespaceThrowableProxyConverter

import java.nio.charset.Charset

conversionRule 'clr', ColorConverter
conversionRule 'wex', WhitespaceThrowableProxyConverter

// See http://logback.qos.ch/manual/groovy.html for details on configuration
appender('STDOUT', ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        charset = Charset.forName('UTF-8')

        String redacted = "%replace(%replace(%msg){'(?i)([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\\\.[A-Za-z]{2,})','[EMAIL_REDACTED]'}){'\\\\b\\\\d{3}[\\\\s-]?\\\\d{3}[\\\\s-]?\\\\d{4}\\\\b','[PHONE_REDACTED]'}"
        pattern =
                '%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} ' +
                        '%clr(%5p) ' +
                        '%clr(---){faint} %clr([%15.15t]){faint} ' +
                        '%clr([cid:%X{correlationId:-none}]){magenta} ' +
                        '%clr(%-40.40logger{39}){cyan} %clr(:){faint} ' +
                        redacted + '%n%wex'
    }
}

def targetDir = BuildSettings.TARGET_DIR
if (Environment.isDevelopmentMode() && targetDir != null) {
    appender("FULL_STACKTRACE", FileAppender) {
        file = "${targetDir}/stacktrace.log"
        append = true
        encoder(PatternLayoutEncoder) {
            pattern = "%level %logger - %replace(%replace(%msg){'(?i)([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})','[EMAIL_REDACTED]'}){'\\b\\d{3}[\\s-]?\\d{3}[\\s-]?\\d{4}\\b','[PHONE_REDACTED]'}%n"
        }
    }
    logger("StackTrace", ERROR, ['FULL_STACKTRACE'], false)
}
root(ERROR, ['STDOUT'])

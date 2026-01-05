package cpservice

import grails.core.GrailsApplication
import grails.plugin.springwebsocket.DefaultWebSocketConfig
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry

@CompileStatic
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig extends DefaultWebSocketConfig {

    @Autowired GrailsApplication grailsApplication

    @Override
    void registerStompEndpoints(StompEndpointRegistry registry) {
        def cfg = grailsApplication.config
        boolean corsEnabled = cfg.getProperty('app.cors.enabled', Boolean, false)
        String[] allowed = (cfg.getProperty('app.cors.allowedOrigins', List, ['*']) as List).toArray(new String[0]) as String[]

        def endpoint = registry.addEndpoint('/wsxpory')
        if (corsEnabled) {
            endpoint.setAllowedOrigins(allowed)
        }
        endpoint.withSockJS()
    }
}

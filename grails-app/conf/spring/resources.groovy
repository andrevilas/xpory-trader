import cpservice.WebSocketConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry

// Place your Spring DSL code here
beans = {
    meterRegistry(SimpleMeterRegistry)
    webSocketConfig(WebSocketConfig)
}

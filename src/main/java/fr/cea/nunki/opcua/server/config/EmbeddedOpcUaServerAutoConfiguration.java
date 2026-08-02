package fr.cea.nunki.opcua.server.config;

/*
 * @requirement REQ-00010 Full-featured OPC-UA integration
 * @feature FR-00010 Embedded OPC-UA Stub Server Launch Options
 * @task TSK-20260311-005 OPC UA Address Space Mocking
 * @compliance CS-0010 Code Traceability
 * @compliance CS-0020 AI Agent Verification Standard
 * @compliance CS-0030 Java Programming Standard
 */

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import fr.cea.nunki.opcua.server.QuasarOpcUaServer;

/**
 * Auto-configuration component that conditionally initializes and starts
 * the embedded Java Quasar OPC-UA Stub Server when {@code opcua.embedded-server.enabled=true}.
 */
@Configuration
@ConditionalOnProperty(name = "opcua.embedded-server.enabled", havingValue = "true")
public class EmbeddedOpcUaServerAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedOpcUaServerAutoConfiguration.class);

    private final EmbeddedOpcUaServerProperties properties;

    public EmbeddedOpcUaServerAutoConfiguration(EmbeddedOpcUaServerProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Bean(destroyMethod = "stop")
    public QuasarOpcUaServer embeddedOpcUaServer() throws Exception {
        logger.info("[EmbeddedOpcUaServer] Launching integrated Java OPC-UA Stub Server on port {} (warmup: {}s)...",
            properties.getPort(), properties.getWarmupDelaySeconds());
        
        QuasarOpcUaServer server = new QuasarOpcUaServer(
            properties.getPort(),
            properties.getWarmupDelaySeconds(),
            properties.isEnableProcessSimulation()
        );

        server.start();
        return server;
    }
}

package fr.lecabellec.nunki.opcua.server.config;

/*
 * @requirement REQ-00010 Full-featured OPC-UA integration
 * @feature FR-00010 Embedded OPC-UA Stub Server Launch Options
 * @task TSK-20260311-005 OPC UA Address Space Mocking
 * @compliance CS-0010 Code Traceability
 * @compliance CS-0020 AI Agent Verification Standard
 * @compliance CS-0030 Java Programming Standard
 */

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the integrated OPC-UA stub server.
 * Maps to prefix {@code opcua.embedded-server}.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "opcua.embedded-server")
public class EmbeddedOpcUaServerProperties {

    /** Whether to start the embedded Java OPC-UA stub server on Spring application startup. */
    private boolean enabled = false;

    /** Port for the embedded OPC-UA server to bind to (default: 4840). */
    private int port = 4840;

    /** Warmup delay in seconds before fast counters start ticking (default: 15 seconds). */
    private long warmupDelaySeconds = 15;

    /** Whether to enable the physical process mimic simulation loop (default: true). */
    private boolean enableProcessSimulation = true;

    // Getters and Setters ---------------------------------------------------

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public long getWarmupDelaySeconds() {
        return warmupDelaySeconds;
    }

    public void setWarmupDelaySeconds(long warmupDelaySeconds) {
        this.warmupDelaySeconds = warmupDelaySeconds;
    }

    public boolean isEnableProcessSimulation() {
        return enableProcessSimulation;
    }

    public void setEnableProcessSimulation(boolean enableProcessSimulation) {
        this.enableProcessSimulation = enableProcessSimulation;
    }
}

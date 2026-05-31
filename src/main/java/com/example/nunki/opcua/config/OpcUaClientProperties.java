package com.example.nunki.opcua.config;

/*
 * REQ-00010 – Full‑featured OPC‑UA client service
 * FR-00010 – Async API for read/write/call/subscribe
 * TSK-00005 – Security configuration (properties definition)
 */

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the OPC‑UA client. Values are taken from
 * {@code application.yml} under the prefix {@code opcua.client}.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "opcua.client")
public class OpcUaClientProperties {

    /** Endpoint URL, e.g. {@code opc.tcp://localhost:4840} */
    private String endpointUrl = "opc.tcp://localhost:4840";

    /** Path to the client X.509 certificate (PEM). Optional – if not set, anonymous or username/password auth is used. */
    private String clientCertificate;

    /** Path to the client private key (PEM). Required when {@code clientCertificate} is set. */
    private String clientKey;

    /** Username for simple username/password authentication. */
    private String username;

    /** Password for username/password authentication. */
    private String password;

    /** Maximum number of retry attempts for a failed request. */
    private int maxAttempts = 3;

    /** Base back‑off time in milliseconds for retries. */
    private long baseBackoffMs = 500;

    // Getters and setters ---------------------------------------------------
    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    public String getClientCertificate() { return clientCertificate; }
    public void setClientCertificate(String clientCertificate) { this.clientCertificate = clientCertificate; }

    public String getClientKey() { return clientKey; }
    public void setClientKey(String clientKey) { this.clientKey = clientKey; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public long getBaseBackoffMs() { return baseBackoffMs; }
    public void setBaseBackoffMs(long baseBackoffMs) { this.baseBackoffMs = baseBackoffMs; }
}

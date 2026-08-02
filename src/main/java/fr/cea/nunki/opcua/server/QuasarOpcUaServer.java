package fr.cea.nunki.opcua.server;

/*
 * @requirement REQ-00010 Full-featured OPC-UA integration
 * @feature FR-00010 Embedded OPC-UA Stub Server
 * @task TSK-20260311-005 OPC UA Address Space Mocking
 * @compliance CS-0010 Code Traceability
 * @compliance CS-0020 AI Agent Verification Standard
 * @compliance CS-0030 Java Programming Standard
 */

import java.security.Security;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.identity.AnonymousIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Full Java-based OPC-UA Stub Server wrapping Eclipse Milo's OpcUaServer.
 * 
 * Replicates the Quasar C++ OPC-UA server address space and behavior for testing
 * and integrated execution.
 */
public class QuasarOpcUaServer implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(QuasarOpcUaServer.class);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final int port;
    private final long warmupDelaySeconds;
    private final boolean enableProcessSimulation;

    private OpcUaServer server;
    private QuasarNamespace namespace;
    private boolean running = false;

    public QuasarOpcUaServer(int port, long warmupDelaySeconds, boolean enableProcessSimulation) {
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535, got: " + port);
        }
        this.port = port;
        this.warmupDelaySeconds = warmupDelaySeconds;
        this.enableProcessSimulation = enableProcessSimulation;
    }

    public QuasarOpcUaServer(int port) {
        this(port, 15, true);
    }

    public QuasarOpcUaServer() {
        this(4840, 15, true);
    }

    public synchronized void start() throws Exception {
        if (running) {
            logger.warn("[QuasarOpcUaServer] Server is already running on port {}", port);
            return;
        }

        logger.info("[QuasarOpcUaServer] Initializing OPC-UA Stub Server on port {}...", port);

        EndpointConfiguration endpointConfig = EndpointConfiguration.newBuilder()
            .setBindAddress("0.0.0.0")
            .setHostname(HostnameUtil.getHostname())
            .setPath("/opcua")
            .setBindPort(port)
            .setTransportProfile(TransportProfile.TCP_UASC_UABINARY)
            .build();

        OpcUaServerConfig serverConfig = OpcUaServerConfig.builder()
            .setApplicationName(LocalizedText.english("Quasar OPC-UA Stub Server"))
            .setApplicationUri("urn:quasar:opcua:server")
            .setProductUri("urn:quasar:opcua:server")
            .setBuildInfo(new BuildInfo(
                "urn:quasar:opcua:server",
                "Nunki",
                "Quasar OPC-UA Stub Server",
                "1.0.0",
                "1",
                DateTime.now()
            ))
            .setEndpoints(Collections.singleton(endpointConfig))
            .setIdentityValidator(AnonymousIdentityValidator.INSTANCE)
            .build();

        server = new OpcUaServer(serverConfig);

        // Register Quasar Namespace
        namespace = new QuasarNamespace(
            server,
            warmupDelaySeconds,
            enableProcessSimulation
        );

        // Startup namespace & server
        namespace.startup();
        CompletableFuture<OpcUaServer> future = server.startup();
        future.get();
        running = true;
        logger.info("[QuasarOpcUaServer] OPC-UA Stub Server successfully started on port {}.", port);
    }

    public synchronized void stop() {
        if (!running || server == null) {
            return;
        }

        logger.info("[QuasarOpcUaServer] Shutting down OPC-UA Stub Server on port {}...", port);
        try {
            if (namespace != null) {
                namespace.shutdown();
                namespace.close();
            }
            server.shutdown().get();
            running = false;
            logger.info("[QuasarOpcUaServer] OPC-UA Stub Server stopped successfully.");
        } catch (Exception e) {
            logger.error("[QuasarOpcUaServer] Error during server shutdown", e);
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public OpcUaServer getServer() {
        return server;
    }

    public QuasarNamespace getNamespace() {
        return namespace;
    }

    @Override
    public void close() {
        stop();
    }
}

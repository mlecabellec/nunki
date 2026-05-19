package com.example.nunki.service;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;

/**
 * Service to connect to a Quasar OPC UA server.
 * [TSK-20260519-001] Implementation of OPC UA Client for Quasar integration.
 */
@Service
public class OpcUaClientService {

    private static final Logger logger = LoggerFactory.getLogger(OpcUaClientService.class);

    @Value("${quasar.opcua.url:opc.tcp://localhost:4840}")
    private String endpointUrl;

    private OpcUaClient client;

    @PostConstruct
    public void init() {
        logger.info("Initializing OPC UA Client for endpoint: {}", endpointUrl);
        try {
            client = OpcUaClient.create(endpointUrl);
            client.connect().get();
            logger.info("Connected to OPC UA Server at {}", endpointUrl);
            
            // Example read
            readMyInt();
        } catch (Exception e) {
            logger.error("Failed to connect to OPC UA Server: {}", e.getMessage());
        }
    }

    public void readMyInt() {
        if (client == null) return;
        try {
            // NodeId for "MyInt" in Quasar (usually in namespace 1 if mapped)
            // Based on TestOpcUaServer.cpp, it's under Root/Data/MyInt
            // We'll need to browse or know the exact NodeId.
            // For now, this is a placeholder for the connection verification.
            NodeId nodeId = new NodeId(1, "Data/MyInt"); 
            client.readValue(0.0, TimestampsToReturn.Both, nodeId)
                .thenAccept(value -> logger.info("Read MyInt value: {}", value.getValue()))
                .exceptionally(ex -> {
                    logger.warn("Could not read MyInt: {}", ex.getMessage());
                    return null;
                });
        } catch (Exception e) {
            logger.error("Error reading from OPC UA: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void tearDown() {
        if (client != null) {
            client.disconnect();
            logger.info("Disconnected OPC UA Client.");
        }
    }
}

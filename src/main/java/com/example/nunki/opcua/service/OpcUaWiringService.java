package com.example.nunki.opcua.service;

/*
 * REQ-00010 – Full‑featured OPC‑UA client service
 * REQ-00014 – Support for websocket/STOMP async exchanges
 * TSK-00103 – OpcUaWiringService implementation
 */

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.example.nunki.opcua.api.OpcUaClientApi;
import com.example.nunki.opcua.dto.OpcUaNodeDto;

@Service
public class OpcUaWiringService {

    private static final Logger logger = LoggerFactory.getLogger(OpcUaWiringService.class);

    private final OpcUaClientApi opcUaClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final ConcurrentHashMap<String, OpcUaClientApi.SubscriptionHandle> activeSubscriptions = new ConcurrentHashMap<>();

    public record OpcUaUpdateMessage(String nodeId, String value, long timestamp) {
        public OpcUaUpdateMessage {
            Objects.requireNonNull(nodeId, "nodeId must not be null");
            Objects.requireNonNull(value, "value must not be null");
        }
    }

    public OpcUaWiringService(OpcUaClientApi opcUaClient, SimpMessagingTemplate messagingTemplate) {
        this.opcUaClient = Objects.requireNonNull(opcUaClient, "opcUaClient must not be null");
        this.messagingTemplate = Objects.requireNonNull(messagingTemplate, "messagingTemplate must not be null");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startWiring() {
        logger.info("[OPC-UA Wiring] Starting OPC UA integration service...");
        CompletableFuture<Void> connectFuture = opcUaClient.connect();
        if (connectFuture == null) {
            logger.warn("[OPC-UA Wiring] opcUaClient.connect() returned null (likely a mock). Skipping wiring startup.");
            return;
        }
        connectFuture
            .thenCompose(v -> {
                logger.info("[OPC-UA Wiring] Connected to OPC UA Server. Starting tree browse and subscription setup...");
                // Start browsing and subscribing from the Objects folder
                return setupSubscriptionsForNode(org.eclipse.milo.opcua.stack.core.Identifiers.ObjectsFolder);
            })
            .exceptionally(ex -> {
                logger.error("[OPC-UA Wiring] Failed to complete OPC UA startup wiring: {}", ex.getMessage());
                return null;
            });
    }

    private CompletableFuture<Void> setupSubscriptionsForNode(NodeId nodeId) {
        return opcUaClient.browseTree(nodeId)
            .thenAccept(rootDto -> {
                logger.info("[OPC-UA Wiring] Browsed OPC-UA tree successfully. Registering subscriptions...");
                registerSubscriptionsRecursive(rootDto);
            });
    }

    private void registerSubscriptionsRecursive(OpcUaNodeDto node) {
        if ("Variable".equalsIgnoreCase(node.getNodeClass())) {
            String nodeIdStr = node.getNodeId();
            NodeId targetNodeId = NodeId.parse(nodeIdStr);
            
            logger.info("[OPC-UA Wiring] Registering subscription for variable node: {}", nodeIdStr);
            
            OpcUaClientApi.SubscriptionParameters params = new OpcUaClientApi.SubscriptionParameters(
                1000.0, 10, 10, 10, true
            );
            
            opcUaClient.subscribe(targetNodeId, value -> {
                Object valObj = value.getValue().getValue();
                String valStr = valObj != null ? valObj.toString() : "";
                
                logger.info("[OPC-UA Wiring] Value change detected for node {}: {}", nodeIdStr, valStr);
                
                OpcUaUpdateMessage update = new OpcUaUpdateMessage(
                    nodeIdStr,
                    valStr,
                    Instant.now().toEpochMilli()
                );
                
                messagingTemplate.convertAndSend("/topic/opcua-tree", update);
            }, params).thenAccept(handle -> {
                activeSubscriptions.put(nodeIdStr, handle);
                logger.info("[OPC-UA Wiring] Subscription activated for: {}", nodeIdStr);
            }).exceptionally(ex -> {
                logger.error("[OPC-UA Wiring] Failed to subscribe to node {}: {}", nodeIdStr, ex.getMessage());
                return null;
            });
        }

        for (OpcUaNodeDto child : node.getChildren()) {
            registerSubscriptionsRecursive(child);
        }
    }
}

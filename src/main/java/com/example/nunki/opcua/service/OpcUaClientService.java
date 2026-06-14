package com.example.nunki.opcua.service;

/*
 * REQ-00010 – Full‑featured OPC‑UA client service
 * FR-00010 – Async API for read/write/call/subscribe
 * TSK-00008 – OpcUaClientService implementation
 */

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.nunki.opcua.api.OpcUaClientApi;
import com.example.nunki.opcua.exception.OpcUaException;
import com.example.nunki.opcua.mapper.DataTypeMapper;
import com.example.nunki.opcua.queue.RequestQueue;
import com.example.nunki.opcua.connection.ConnectionManager;
import com.example.nunki.opcua.subscription.SubscriptionManager;
import com.example.nunki.opcua.dto.OpcUaNodeDto;

/**
 * Facade implementation that delegates all operations to the lower‑level
 * managers. All public methods return {@link CompletableFuture} and wrap any
 * Milo exceptions in {@link OpcUaException}.
 */
@Service("facadeOpcUaClientService")
public class OpcUaClientService implements OpcUaClientApi {

    private static final Logger logger = LoggerFactory.getLogger(OpcUaClientService.class);

    private final ConnectionManager connectionManager;
    private final RequestQueue requestQueue;
    private final DataTypeMapper mapper;
    private final SubscriptionManager subscriptionManager;

    public OpcUaClientService(ConnectionManager connectionManager,
                              RequestQueue requestQueue,
                              DataTypeMapper mapper,
                              SubscriptionManager subscriptionManager) {
        this.connectionManager = connectionManager;
        this.requestQueue = requestQueue;
        this.mapper = mapper;
        this.subscriptionManager = subscriptionManager;
    }

    @Override
    public CompletableFuture<Void> connect() {
        return connectionManager.getClient().thenAccept(s -> logger.info("OPC-UA client connected"));
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        return connectionManager.disconnect();
    }

    @Override
    public CompletableFuture<DataValue> read(NodeId nodeId, TimestampsToReturn timestamps) {
        return requestQueue.submit(() ->
                connectionManager.getClient()
                    .thenCompose(client -> client.readValue(0.0, timestamps, nodeId))
        ).exceptionally(ex -> {
            throw new OpcUaException("Read failed for node " + nodeId, ex, true, null);
        });
    }

    @Override
    public CompletableFuture<StatusCode> write(NodeId nodeId, Variant value) {
        return requestQueue.submit(() ->
                connectionManager.getClient()
                    .thenCompose(client -> client.writeValue(nodeId, new DataValue(value)))
        ).exceptionally(ex -> {
            throw new OpcUaException("Write failed for node " + nodeId, ex, true, null);
        });
    }

    @Override
    public CompletableFuture<MethodResult> call(NodeId objectId, NodeId methodId, List<Variant> inputArguments) {
        return requestQueue.submit(() ->
                connectionManager.getClient()
                    .thenCompose(client -> {
                        // Build the CallMethodRequest structure expected by Milo
                        org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest request =
                                new org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest(
                                        objectId,
                                        methodId,
                                        inputArguments.toArray(new Variant[0]));
                        return client.call(request);
                    })
        ).thenApply(res -> new MethodResult(java.util.Arrays.asList(res.getOutputArguments()), res.getStatusCode()))
         .exceptionally(ex -> {
            throw new OpcUaException("Method call failed for method " + methodId, ex, true, null);
         });
    }

    @Override
    public CompletableFuture<SubscriptionHandle> subscribe(NodeId nodeId, OnDataChange onChange, SubscriptionParameters params) {
        return subscriptionManager.subscribe(nodeId, onChange, params);
    }

    @Override
    public CompletableFuture<Void> unsubscribe(SubscriptionHandle handle) {
        return subscriptionManager.unsubscribe(handle);
    }

    @Override
    public CompletableFuture<OpcUaNodeDto> browseTree(NodeId rootNodeId) {
        java.util.Objects.requireNonNull(rootNodeId, "rootNodeId must not be null");
        return connectionManager.getClient().thenCompose(client -> browseNodeRecursive(client, rootNodeId));
    }

    private CompletableFuture<OpcUaNodeDto> browseNodeRecursive(org.eclipse.milo.opcua.sdk.client.OpcUaClient client, NodeId nodeId) {
        return client.getAddressSpace().getNodeAsync(nodeId).thenCompose(node -> {
            String name = node.getDisplayName().getText();
            if (name == null) {
                name = node.getBrowseName().getName();
            }
            if (name == null) {
                name = nodeId.toParseableString();
            }
            final String finalName = name;
            final String nodeClass = node.getNodeClass().name();

            CompletableFuture<String> valueFuture;
            if (node.getNodeClass() == org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass.Variable) {
                valueFuture = client.readValue(0.0, TimestampsToReturn.Both, nodeId)
                    .thenApply(val -> {
                        if (val.getValue().getValue() != null) {
                            return val.getValue().getValue().toString();
                        }
                        return "";
                    }).exceptionally(ex -> "");
            } else {
                valueFuture = CompletableFuture.completedFuture("");
            }

            org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription browseDesc =
                new org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription(
                    nodeId,
                    org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection.Forward,
                    org.eclipse.milo.opcua.stack.core.Identifiers.HierarchicalReferences,
                    true,
                    org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint(
                        org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass.Object.getValue() |
                        org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass.Variable.getValue() |
                        org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass.Method.getValue()
                    ),
                    org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint(
                        org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask.All.getValue()
                    )
                );

            CompletableFuture<org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult> browseFuture = client.browse(browseDesc);

            return valueFuture.thenCombine(browseFuture, (value, browseResult) -> {
                org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription[] refs = browseResult.getReferences();
                if (refs == null || refs.length == 0) {
                    return CompletableFuture.completedFuture(
                        new OpcUaNodeDto(nodeId.toParseableString(), finalName, nodeClass, value, java.util.Collections.emptyList())
                    );
                }

                List<CompletableFuture<OpcUaNodeDto>> childFutures = new java.util.ArrayList<>();
                for (org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription ref : refs) {
                    NodeId childNodeId = ref.getNodeId().toNodeId(client.getNamespaceTable()).orElse(null);
                    if (childNodeId != null) {
                        // Avoid deep recursion into standard system Server node
                        if ("Server".equals(ref.getBrowseName().getName())) {
                            continue;
                        }
                        childFutures.add(browseNodeRecursive(client, childNodeId));
                    }
                }

                if (childFutures.isEmpty()) {
                    return CompletableFuture.completedFuture(
                        new OpcUaNodeDto(nodeId.toParseableString(), finalName, nodeClass, value, java.util.Collections.emptyList())
                    );
                }

                CompletableFuture<Void> allChildrenFuture = CompletableFuture.allOf(childFutures.toArray(new CompletableFuture[0]));
                return allChildrenFuture.thenApply(v -> {
                    List<OpcUaNodeDto> childrenList = new java.util.ArrayList<>();
                    for (CompletableFuture<OpcUaNodeDto> cf : childFutures) {
                        childrenList.add(cf.join());
                    }
                    return new OpcUaNodeDto(nodeId.toParseableString(), finalName, nodeClass, value, childrenList);
                });
            }).thenCompose(f -> f);
        });
    }
}

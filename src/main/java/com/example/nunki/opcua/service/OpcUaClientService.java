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
}

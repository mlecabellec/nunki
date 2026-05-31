package com.example.nunki.opcua.api;

/*
 * REQ-00010 – Full‑featured OPC‑UA client service
 * FR-00010 – Async API for read/write/call/subscribe
 * TSK-00006 – Read/Write API definition
 */

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

/**
 * Public façade for the OPC‑UA client. All operations are asynchronous and return
 * {@link CompletableFuture} so they can be composed or awaited as needed.
 */
public interface OpcUaClientApi {

    /** Connects (or reconnects) to the OPC‑UA server. Idempotent. */
    CompletableFuture<Void> connect();

    /** Gracefully disconnects from the server. */
    CompletableFuture<Void> disconnect();

    /** Reads the value of a node. */
    CompletableFuture<DataValue> read(NodeId nodeId, TimestampsToReturn timestamps);

    /** Writes a value to a node. */
    CompletableFuture<StatusCode> write(NodeId nodeId, Variant value);

    /** Calls a method on a node. */
    CompletableFuture<MethodResult> call(NodeId objectId, NodeId methodId, List<Variant> inputArguments);

    /** Subscribes to data changes on a node. */
    CompletableFuture<SubscriptionHandle> subscribe(NodeId nodeId,
                                                   OnDataChange onChange,
                                                   SubscriptionParameters params);

    /** Unsubscribes a previously created subscription. */
    CompletableFuture<Void> unsubscribe(SubscriptionHandle handle);

    /** Simple holder for method call results. */
    record MethodResult(List<Variant> outputArguments, StatusCode status) {}

    /** Listener for data‑change notifications. */
    @FunctionalInterface
    interface OnDataChange {
        void onChange(DataValue value);
    }

    /** Parameters used when creating a subscription. */
    record SubscriptionParameters(
            double requestedPublishingInterval,
            int requestedLifetimeCount,
            int requestedMaxKeepAliveCount,
            int maxNotificationsPerPublish,
            boolean discardOldest) {}

    /** Identifier for a subscription created by the client. */
    record SubscriptionHandle(long subscriptionId, NodeId nodeId) {}
}

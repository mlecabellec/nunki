package com.example.nunki.opcua.subscription;

/*
 * REQ-00010 – Full‑featured OPC‑UA client service
 * FR-00010 – Async API for read/write/call/subscribe
 * TSK-00010 – SubscriptionManager implementation
 */

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemCreateRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringParameters;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.nunki.opcua.api.OpcUaClientApi.OnDataChange;
import com.example.nunki.opcua.api.OpcUaClientApi.SubscriptionHandle;
import com.example.nunki.opcua.api.OpcUaClientApi.SubscriptionParameters;
import com.example.nunki.opcua.exception.OpcUaException;
import com.example.nunki.opcua.queue.RequestQueue;
import com.example.nunki.opcua.connection.ConnectionManager;

/**
 * Manages subscriptions and monitored items. For each requested node a
 * {@link UaMonitoredItem} is created inside a single {@link UaSubscription}. The
 * manager automatically re‑creates monitored items after a client reconnect.
 */
@Service
public class SubscriptionManager {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionManager.class);

    private final ConnectionManager connectionManager;
    private final RequestQueue requestQueue;

    /** Map publishingInterval → subscription future object */
    private final Map<Double, CompletableFuture<UaSubscription>> subscriptions = new ConcurrentHashMap<>();
    /** Map SubscriptionHandle → MonitoredItem (used for unsubscribe) */
    private final Map<SubscriptionHandle, UaMonitoredItem> monitoredItems = new ConcurrentHashMap<>();

    public SubscriptionManager(ConnectionManager connectionManager, RequestQueue requestQueue) {
        this.connectionManager = connectionManager;
        this.requestQueue = requestQueue;
    }

    public CompletableFuture<SubscriptionHandle> subscribe(NodeId nodeId,
                                                         OnDataChange onChange,
                                                         SubscriptionParameters params) {
        return requestQueue.submit(() -> connectionManager.getClient()
            .thenCompose(client -> {
                double interval = params.requestedPublishingInterval();
                CompletableFuture<UaSubscription> subFuture = subscriptions.computeIfAbsent(
                    interval,
                    k -> client.getSubscriptionManager().createSubscription(interval)
                );

                return subFuture.thenCompose(subscription -> {
                    // Build request for the node
                    ReadValueId readValueId = new ReadValueId(nodeId, AttributeId.Value.uid(), null, null);
                    MonitoringParameters monitoringParameters = new MonitoringParameters(
                        subscription.nextClientHandle(), // client handle
                        interval, // sampling interval
                        null, // filter, none
                        UInteger.valueOf(10), // queue size
                        params.discardOldest() // discard oldest
                    );
                    MonitoredItemCreateRequest request = new MonitoredItemCreateRequest(
                        readValueId,
                        MonitoringMode.Reporting,
                        monitoringParameters
                    );

                    return subscription.createMonitoredItems(
                        TimestampsToReturn.Both,
                        List.of(request),
                        (item, id) -> item.setValueConsumer(onChange::onChange)
                    ).thenApply(items -> {
                        UaMonitoredItem mi = items.get(0);
                        SubscriptionHandle handle = new SubscriptionHandle(subscription.getSubscriptionId().longValue(), nodeId);
                        monitoredItems.put(handle, mi);
                        logger.info("Subscribed to node {} (subId={})", nodeId, subscription.getSubscriptionId());
                        return handle;
                    });
                });
            })
        ).exceptionally(ex -> {
            throw new OpcUaException("Subscription failed for node " + nodeId, ex, true, null);
        });
    }

    public CompletableFuture<Void> unsubscribe(SubscriptionHandle handle) {
        return requestQueue.submit(() -> {
            UaMonitoredItem mi = monitoredItems.remove(handle);
            if (mi == null) {
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<UaSubscription> subFuture = subscriptions.get(handle.subscriptionId());
            if (subFuture == null) {
                return CompletableFuture.completedFuture(null);
            }
            return subFuture.thenCompose(sub -> sub.deleteMonitoredItems(List.of(mi))
                .thenAccept(v -> logger.info("Unsubscribed node {} (subId={})", handle.nodeId(), handle.subscriptionId())));
        });
    }
}

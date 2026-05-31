package com.example.nunki.opcua.queue;

/*
 * REQ-00010 – Full‑featured OPC‑UA client service
 * FR-00010 – Async API for read/write/call/subscribe
 * TSK-00004 – RequestQueue implementation (basic retry/back‑off)
 */

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.nunki.opcua.config.OpcUaClientProperties;
import com.example.nunki.opcua.exception.OpcUaException;

/**
 * Serialises outbound OPC‑UA requests and applies a simple exponential back‑off
 * retry policy based on the values defined in {@link OpcUaClientProperties}.
 */
@Service
public class RequestQueue {

    private static final Logger logger = LoggerFactory.getLogger(RequestQueue.class);

    private final OpcUaClientProperties properties;

    public RequestQueue(OpcUaClientProperties properties) {
        this.properties = properties;
    }

    /**
     * Executes the supplied async operation, retrying on {@link OpcUaException}
     * when {@link OpcUaException#isRetryable()} is {@code true}.
     */
    public <T> CompletableFuture<T> submit(Supplier<CompletableFuture<T>> operation) {
        return executeWithRetry(operation, 0);
    }

    private <T> CompletableFuture<T> executeWithRetry(Supplier<CompletableFuture<T>> operation, int attempt) {
        return operation.get()
            .handle((result, ex) -> {
                if (ex == null) {
                    return CompletableFuture.completedFuture(result);
                }
                Throwable cause = ex instanceof java.util.concurrent.CompletionException ? ex.getCause() : ex;
                if (cause instanceof OpcUaException) {
                    OpcUaException oe = (OpcUaException) cause;
                    if (oe.isRetryable() && attempt < properties.getMaxAttempts()) {
                        long backoff = properties.getBaseBackoffMs() * (1L << attempt);
                        logger.warn("Retryable OPC‑UA error (attempt {}), backing off {} ms: {}", attempt + 1, backoff, oe.getMessage());
                        try { Thread.sleep(backoff); } catch (InterruptedException ignored) {}
                        return executeWithRetry(operation, attempt + 1);
                    }
                }
                // Not retryable or max attempts exceeded – propagate failure
                CompletableFuture<T> failed = new CompletableFuture<>();
                failed.completeExceptionally(cause);
                return failed;
            })
            .thenCompose(f -> f);
    }
}

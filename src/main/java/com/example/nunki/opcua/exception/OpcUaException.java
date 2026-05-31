package com.example.nunki.opcua.exception;

/*
 * REQ-00010 – Full‑featured OPC‑UA client service
 * FR-00010 – Async API for read/write/call/subscribe
 * TSK-00004 – RequestQueue & Exception definition
 */

import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;

/**
 * Wrapper for exceptions coming from the OPC‑UA stack. It carries the underlying
 * {@link StatusCode} when available and a flag indicating whether the operation
 * can be retried.
 */
public class OpcUaException extends RuntimeException {
    private final boolean retryable;
    private final StatusCode statusCode;

    public OpcUaException(String message, Throwable cause) {
        this(message, cause, false, null);
    }

    public OpcUaException(String message, Throwable cause, boolean retryable, StatusCode statusCode) {
        super(message, cause);
        this.retryable = retryable;
        this.statusCode = statusCode;
    }

    public OpcUaException(UaException uaException, boolean retryable) {
        super(uaException);
        this.retryable = retryable;
        this.statusCode = uaException.getStatusCode();
    }

    public boolean isRetryable() {
        return retryable;
    }

    public StatusCode getStatusCode() {
        return statusCode;
    }
}

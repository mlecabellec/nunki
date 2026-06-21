package com.example.nunki.controller;

/*
 * REQ-00010 – Full‑featured OPC‑UA client service
 * TSK-00104 – OPC-UA REST Controller implementation
 */

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.nunki.opcua.api.OpcUaClientApi;
import com.example.nunki.opcua.dto.OpcUaNodeDto;

@RestController
public class OpcUaController {

    private static final Logger logger = LoggerFactory.getLogger(OpcUaController.class);

    private final OpcUaClientApi opcUaClient;

    public OpcUaController(OpcUaClientApi opcUaClient) {
        this.opcUaClient = Objects.requireNonNull(opcUaClient, "opcUaClient must not be null");
    }

    public record OpcUaWriteRequest(String nodeId, String value, String type) {
        public OpcUaWriteRequest {
            Objects.requireNonNull(nodeId, "nodeId must not be null");
            Objects.requireNonNull(value, "value must not be null");
            Objects.requireNonNull(type, "type must not be null");
        }
    }

    public record OpcUaWriteResponse(boolean success, long statusCode) {}

    public record OpcUaInvokeRequest(String objectId, String methodId, List<String> arguments) {
        public OpcUaInvokeRequest {
            Objects.requireNonNull(objectId, "objectId must not be null");
            Objects.requireNonNull(methodId, "methodId must not be null");
            Objects.requireNonNull(arguments, "arguments must not be null");
        }
    }

    public record OpcUaInvokeResponse(boolean success, String result, long statusCode) {
        public OpcUaInvokeResponse {
            Objects.requireNonNull(result, "result must not be null");
        }
    }

    @GetMapping("/api/opcua/tree")
    public CompletableFuture<ResponseEntity<OpcUaNodeDto>> getTree() {
        logger.info("[OPC-UA REST] Fetching OPC-UA node tree starting from ObjectsFolder");
        return opcUaClient.browseTree(org.eclipse.milo.opcua.stack.core.Identifiers.ObjectsFolder)
            .thenApply(tree -> {
                java.util.Optional<OpcUaNodeDto> rootNode = findNodeByName(tree, "Root");
                return ResponseEntity.ok(rootNode.orElse(tree));
            })
            .exceptionally(ex -> {
                logger.error("[OPC-UA REST] Failed to browse OPC-UA tree: {}", ex.getMessage());
                // Return an empty node Dto to avoid passing null
                OpcUaNodeDto emptyDto = new OpcUaNodeDto(
                    "ns=1;s=Root",
                    "Root",
                    "Object",
                    "",
                    java.util.Collections.emptyList()
                );
                return ResponseEntity.status(500).body(emptyDto);
            });
    }

    private java.util.Optional<OpcUaNodeDto> findNodeByName(OpcUaNodeDto node, String name) {
        if (name.equalsIgnoreCase(node.getName())) {
            return java.util.Optional.of(node);
        }
        for (OpcUaNodeDto child : node.getChildren()) {
            java.util.Optional<OpcUaNodeDto> found = findNodeByName(child, name);
            if (found.isPresent()) {
                return found;
            }
        }
        return java.util.Optional.empty();
    }

    @PostMapping("/api/opcua/write")
    public CompletableFuture<ResponseEntity<OpcUaWriteResponse>> writeValue(@RequestBody OpcUaWriteRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        logger.info("[OPC-UA REST] Write request received for node {}: value={}, type={}", 
            request.nodeId(), request.value(), request.type());

        NodeId parsedNodeId = NodeId.parse(request.nodeId());
        Object parsedValue;
        switch (request.type().toLowerCase()) {
            case "boolean":
                parsedValue = Boolean.valueOf(request.value());
                break;
            case "int":
            case "int32":
            case "integer":
                parsedValue = Integer.valueOf(request.value());
                break;
            case "double":
            case "float":
                parsedValue = Double.valueOf(request.value());
                break;
            default:
                parsedValue = request.value();
                break;
        }

        Variant variant = new Variant(parsedValue);
        return opcUaClient.write(parsedNodeId, variant)
            .thenApply(statusCode -> {
                boolean success = statusCode.isGood();
                logger.info("[OPC-UA REST] Write completed for node {}: success={}, statusCode=0x{}", 
                    request.nodeId(), success, Long.toHexString(statusCode.getValue()));
                return ResponseEntity.ok(new OpcUaWriteResponse(success, statusCode.getValue()));
            })
            .exceptionally(ex -> {
                logger.error("[OPC-UA REST] Failed to write value to node {}: {}", request.nodeId(), ex.getMessage());
                return ResponseEntity.status(500).body(new OpcUaWriteResponse(false, 0xFFFFFFFFL));
            });
    }

    @PostMapping("/api/opcua/invoke")
    public CompletableFuture<ResponseEntity<OpcUaInvokeResponse>> invokeMethod(@RequestBody OpcUaInvokeRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        logger.info("[OPC-UA REST] Invoke method request received: methodId={}, objectId={}", 
            request.methodId(), request.objectId());

        NodeId parsedObjectId = NodeId.parse(request.objectId());
        NodeId parsedMethodId = NodeId.parse(request.methodId());

        java.util.List<Variant> variants = new java.util.ArrayList<>();
        for (String arg : request.arguments()) {
            variants.add(new Variant(arg));
        }

        return opcUaClient.call(parsedObjectId, parsedMethodId, variants)
            .thenApply(methodResult -> {
                boolean success = methodResult.status().isGood();
                String resStr = "";
                if (!methodResult.outputArguments().isEmpty()) {
                    Object outVal = methodResult.outputArguments().get(0).getValue();
                    resStr = outVal != null ? outVal.toString() : "";
                }
                logger.info("[OPC-UA REST] Method invocation completed for {}: success={}, result={}", 
                    request.methodId(), success, resStr);
                return ResponseEntity.ok(new OpcUaInvokeResponse(success, resStr, methodResult.status().getValue()));
            })
            .exceptionally(ex -> {
                logger.error("[OPC-UA REST] Failed to invoke method {}: {}", request.methodId(), ex.getMessage());
                return ResponseEntity.status(500).body(new OpcUaInvokeResponse(false, "", 0xFFFFFFFFL));
            });
    }
}

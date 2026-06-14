package com.example.nunki.opcua.dto;

/*
 * REQ-00010 – Full‑featured OPC‑UA client service
 * TSK-00102 – OPC-UA Node DTO definition
 */

import java.util.List;
import java.util.Objects;

public class OpcUaNodeDto {
    private final String nodeId;
    private final String name;
    private final String nodeClass;
    private final String value;
    private final List<OpcUaNodeDto> children;

    public OpcUaNodeDto(String nodeId, String name, String nodeClass, String value, List<OpcUaNodeDto> children) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.nodeClass = Objects.requireNonNull(nodeClass, "nodeClass must not be null");
        this.value = value; // Nullable for folders/methods
        this.children = Objects.requireNonNull(children, "children must not be null");
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getName() {
        return name;
    }

    public String getNodeClass() {
        return nodeClass;
    }

    public String getValue() {
        return value;
    }

    public List<OpcUaNodeDto> getChildren() {
        return children;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpcUaNodeDto that = (OpcUaNodeDto) o;
        return Objects.equals(nodeId, that.nodeId) &&
               Objects.equals(name, that.name) &&
               Objects.equals(nodeClass, that.nodeClass) &&
               Objects.equals(value, that.value) &&
               Objects.equals(children, that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, name, nodeClass, value, children);
    }
}

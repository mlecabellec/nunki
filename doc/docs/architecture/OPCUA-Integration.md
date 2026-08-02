# OPC-UA Full-Stack Integration Architecture

This document describes the design, implementation, and verification plan for the integration between the **C++ Quasar OPC UA Server**, the **Java Spring Boot Backend (OPC UA Client & WebSocket Broker)**, and the **Svelte 5 Frontend**.

---

## 1. System Topology

```mermaid
graph TD
    subgraph Frontend [Svelte 5 UI]
        TreeUI["Tree View Component (OpcUaTreeNode.svelte)"]
        WSClient["STOMP WebSocket Manager (websocket.svelte.ts)"]
    end

    subgraph Backend [Spring Boot]
        RestCtrl["REST Controller (OpcUaController.java)"]
        WiringSvc["STOMP Publisher (OpcUaWiringService.java)"]
        ClientSvc["OPC-UA Client Service (OpcUaClientService.java)"]
        ConnMgr["Connection Manager (ConnectionManager.java)"]
    end

    subgraph OPCUAServer [Quasar OPC-UA Server]
        QuasarSrv["C++ Quasar Server (port 4840)"]
    end

    TreeUI <--> |REST API /api/opcua/*| RestCtrl
    TreeUI <--> |STOMP Sub /topic/opcua-tree| WSClient
    WSClient <--> |WebSocket Connection /ws-stomp| WiringSvc
    RestCtrl --> ClientSvc
    WiringSvc --> ClientSvc
    ClientSvc --> ConnMgr
    ConnMgr <--> |OPC-UA binary protocol| QuasarSrv
```

---

## 2. Sequence Workflow

### A. Initialization & Real-Time Monitoring
1. On **Startup**, the Spring Boot backend (`OpcUaWiringService`) establishes an OPC-UA connection to the Quasar Server.
2. The backend recursively browses the OPC-UA address space starting from `ns=1;s=Data` and obtains the tree structure.
3. For every **Variable** node encountered (such as `MySwitch`), the backend registers a subscription monitor.
4. When a variable's value changes on the OPC-UA server, the change triggers the subscription callback. The backend serializes the change to an `OpcUaUpdateMessage` and broadcasts it to `/topic/opcua-tree` via STOMP.
5. Svelte 5 frontend component (`OpcUaTreeNode.svelte`) listens to the STOMP updates reactively via `$derived(wsManager.opcUaUpdates)` and updates the displayed value in real time.

### B. Write Operations
1. The user inputs a new value in `OpcUaTreeNode.svelte` and clicks **Save**.
2. The frontend invokes `wsManager.writeOpcUaValue()`, executing a `POST` request to `/api/opcua/write`.
3. The REST controller parses the value type (e.g. `Boolean`, `Integer`, `Double`, `String`) and converts it to a Milo `Variant`.
4. The backend writes the new value to the OPC-UA server.
5. The write triggers the OPC-UA server state change, which automatically propagates back to the frontend via the subscription channel.

### C. Method Invocation
1. The user clicks **Invoke** on a Method node (such as `ToggleSwitch()`) in `OpcUaTreeNode.svelte`.
2. The frontend invokes `wsManager.invokeOpcUaMethod()`, sending a `POST` request to `/api/opcua/invoke`.
3. The backend executes `opcUaClient.call(...)` with the method's parent object ID and method ID.
4. The C++ server runs the method logic (e.g., toggling `MySwitch` internally) and returns a result.
5. The REST controller sends the execution status and output back to the frontend.

---

## 3. Endpoints Specification

### REST Endpoints
* **`GET /api/opcua/tree`**
  * *Description*: Fetches the complete browsed OPC-UA node hierarchy starting from `ns=1;s=Data`.
  * *Response*: `OpcUaNodeDto` (JSON Tree).
* **`POST /api/opcua/write`**
  * *Description*: Writes a value to an OPC-UA Variable node.
  * *Request Body*: `OpcUaWriteRequest { nodeId: String, value: String, type: String }`
  * *Response*: `OpcUaWriteResponse { success: boolean, statusCode: long }`
* **`POST /api/opcua/invoke`**
  * *Description*: Invokes an OPC-UA Method node.
  * *Request Body*: `OpcUaInvokeRequest { objectId: String, methodId: String, arguments: List<String> }`
  * *Response*: `OpcUaInvokeResponse { success: boolean, result: String, statusCode: long }`

### WebSocket STOMP Channels
* **Subscription Destination**: `/topic/opcua-tree`
  * *Payload*: `OpcUaUpdateMessage { nodeId: String, value: String, timestamp: long }`

---

## 6. Integrated Java OPC-UA Stub Server (`QuasarOpcUaServer`)

To support functional requirements and automated continuous integration testing without requiring external C++ binaries, Nunki embeds a full Java-based OPC-UA Stub Server built on **Eclipse Milo (`sdk-server`)**.

### Configuration & Launch Options
The embedded server can be toggled and configured via `application.yml` or Spring Boot command-line options:

```yaml
opcua:
  embedded-server:
    enabled: true                  # Launches the integrated server on application startup
    port: 4840                     # Target TCP port (default: 4840)
    warmup-delay-seconds: 15       # Warmup delay before fast counters begin ticking
    enable-process-simulation: true # Enables the 100 ms tank level / pump mimic simulation
```

Command-line launch option:
```bash
java -jar target/nunki-0.0.1-SNAPSHOT.jar --opcua.embedded-server.enabled=true
```

### Address Space Mirroring
The Java stub server mirrors the exact 4-branch address space of the C++ Quasar server:
1. **`LargeTree` Branch**: 1,000 synthetic nodes (5 levels, 539 L5 leaves with round-robin types `Int32`, `Boolean`, `Double`, `String`, `Method`).
2. **`CounterControl` Branch**: `CounterValue` (`Int32`), `Increment()` (+1), and `Decrement()` (-1) RPC methods.
3. **`FastCounters` Branch**: `Counter_1Hz` to `Counter_100Hz` with tick rate scaling and 15s warmup delay.
4. **`Data` Branch & Mimic Simulation**: `MyInt`, `MySwitch`, `PumpRunning`, `TankLevel`, `ToggleSwitch()` method, `ExecuteScript()` method with LuaJ engine execution, and a 100 ms process mimic simulation loop.

---

## 7. Verification & Testing Matrix

### Embedded Server & E2E Integration Test Suites
* **`QuasarOpcUaServerTest.java`**: Direct unit and integration tests for `QuasarOpcUaServer` and `QuasarNamespace`.
  * Verifies 1,000-node `LargeTree` browsing.
  * Verifies read/write operations on variables (`MyInt`, `MySwitch`).
  * Verifies RPC method calls (`Increment`, `Decrement`, `ToggleSwitch`).
  * Verifies `ExecuteScript` Lua script execution returning JSON logs and status envelopes.
  * Verifies 100 ms process mimic simulation tank level dynamics.
* **`OpcUaEmbeddedServerIntegrationTest.java`**: Tests Spring Boot startup when `opcua.embedded-server.enabled=true`.
* **`OpcUaDisabledServerIntegrationTest.java`**: Tests Spring Boot startup when `opcua.embedded-server.enabled=false`.
* **`OpcUaEndToEndRestStompTest.java`**: Full-chain integration test validating REST tree browsing, writes, method calls, and STOMP `/topic/opcua-tree` telemetry broadcasts against the live embedded server.
* **`EmbeddedOpcUaPlaywrightTest.java`**: Playwright browser emulation tests executing UI interactions against the live Spring app and integrated OPC-UA server.

---

## 8. Architectural Quality Standards Compliance (CS-0020 / CS-0030)

1. **Precondition Rejections [CS-0030.1]**: Every DTO constructor, server method, and REST endpoint explicitly enforces null checks (`Objects.requireNonNull`) and parameter bounds.
2. **Null Safety [CS-0030.2]**: No endpoint or DTO method allows or propagates `null` values. Fallbacks return empty lists or empty strings.
3. **Explicit Typing [CS-0030.10]**: No usage of `var` type inference in Java backend code. Every type is statically and explicitly declared.
4. **Constructor Injection [CS-0030.13]**: Components use constructor-based dependency injection with immutable final fields.
5. **Traceability [CS-0030.15]**: All files contain header comments mapping them to the specific task and requirements.

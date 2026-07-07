# Navigation by System Components

This index organizes Nunki Control Panel specifications and files based on the physical components of the system (Frontend tier, Backend tier, and External protocols).

---

## 💻 Frontend Svelte Components

The client-side interface contains modular HMI controls:

### 1. Navigation Header
- **Specification**: [Navbar component analysis](Svelte-Frontend.md#e-navigation-header-dropdowns-navbarsvelte)
- **Visual Wireframe**: [Navbar Salt diagram](Svelte-Component-Wireframes.md#3-navigation-header--submenus-navbarsvelte)
- **Source File**: [Navbar.svelte](file:///home/vortigern/git/nunki/src/main/frontend/src/lib/Navbar.svelte)

### 2. Live Process mimic HMI
- **Specification**: [SynopticView component analysis](Svelte-Frontend.md#g-svg-process-mimic-simulator-synopticviewsvelte)
- **Visual Wireframe**: [Synoptics Salt diagram](Svelte-Component-Wireframes.md#5-industrial-mimic-synoptic-diagram-synopticviewsvelte)
- **Source File**: [SynopticView.svelte](file:///home/vortigern/git/nunki/src/main/frontend/src/lib/SynopticView.svelte)

### 3. Real-time Telemetry Graph
- **Specification**: [TimeSeriesChart component analysis](Svelte-Frontend.md#h-svg-chart-plottings-timeserieschartsvelte)
- **Visual Wireframe**: [Trend Chart Salt diagram](Svelte-Component-Wireframes.md#6-real-time-telemetry-trend-chart-timeserieschartsvelte)
- **Source File**: [TimeSeriesChart.svelte](file:///home/vortigern/git/nunki/src/main/frontend/src/lib/TimeSeriesChart.svelte)

### 4. Recursive OPC-UA Address Space Navigator
- **Specification**: [OpcUaTreeNode component analysis](Svelte-Frontend.md#f-collapsible-hierarchical-node-navigator-opcuatreenodesvelte)
- **Visual Wireframe**: [Address Tree Salt diagram](Svelte-Component-Wireframes.md#4-opc-ua-address-space-navigator-opcuatreenodesvelte)
- **Source File**: [OpcUaTreeNode.svelte](file:///home/vortigern/git/nunki/src/main/frontend/src/lib/OpcUaTreeNode.svelte)

---

## ☕ Java Backend Components

The server-side Spring Boot component hierarchy coordinates industrial connections:

### 1. OPC-UA Client Engine
- **Specification**: [OpcUaClientService specification](OPCUA-Integration.md#2-class-diagram--key-java-files)
- **Source File**: [OpcUaClientService.java](file:///home/vortigern/git/nunki/src/main/java/com/example/nunki/opcua/service/OpcUaClientService.java)

### 2. WebSocket Telemetry Wiring
- **Specification**: [OpcUaWiringService specification](OPCUA-Integration.md#2-class-diagram--key-java-files)
- **Source File**: [OpcUaWiringService.java](file:///home/vortigern/git/nunki/src/main/java/com/example/nunki/opcua/service/OpcUaWiringService.java)

### 3. REST Control Deck
- **Specification**: [OpcUaController REST specification](OPCUA-Integration.md#2-class-diagram--key-java-files)
- **Source File**: [OpcUaController.java](file:///home/vortigern/git/nunki/src/main/java/com/example/nunki/controller/OpcUaController.java)

### 4. Thread & Queue Managers
- **Specification**: [RequestQueue & SubscriptionManager specifications](OPCUA-Integration.md#2-class-diagram--key-java-files)
- **Source Files**: [RequestQueue.java](file:///home/vortigern/git/nunki/src/main/java/com/example/nunki/opcua/queue/RequestQueue.java), [SubscriptionManager.java](file:///home/vortigern/git/nunki/src/main/java/com/example/nunki/opcua/subscription/SubscriptionManager.java)

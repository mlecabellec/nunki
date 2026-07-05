# Nunki Dashboard: Svelte 5 Frontend & Spring Boot Backend Architecture

This document provides a comprehensive architectural analysis and design reference for the **Nunki Control Panel**, a real-time industrial telemetry monitoring dashboard. It details the directory structure, components, state management using Svelte 5 Runes, and full-stack integration with the Spring Boot backend and the target OPC-UA Server.

---

## 1. Directory Structure & Layout Analysis

The Svelte frontend is situated in the directory `src/main/frontend`. The hierarchy of source files is organized as follows:

```
src/main/frontend/
├── index.html                     # Core HTML file containing root mount div#app
├── package.json                   # Project dependencies and build scripts
├── svelte.config.js               # Configuration settings for Svelte 5
├── tsconfig.json                  # Shared compiler configurations
├── vite.config.ts                 # Vite configuration setting up dev environment
└── src/
    ├── main.ts                    # Application bootstrapping entrypoint
    ├── app.css                    # Global styles, variables, light/dark themes
    ├── App.svelte                 # Main application shell & router
    └── lib/
        ├── Counter.svelte         # Basic counter template helper
        ├── Navbar.svelte          # Top navigation header dropdown bar
        ├── OpcUaTreeNode.svelte   # Collapsible recursive OPC-UA tree nodes
        ├── SynopticView.svelte    # SVG pipe fluid mimic diagram simulator
        ├── TimeSeriesChart.svelte # Native SVG line plot chart
        └── websocket.svelte.ts    # STOMP client manager and shared states
```

### Key Java Backend Architecture Files:
* [NunkiApplication.java](file:///home/vortigern/git/nunki/src/main/java/com/example/nunki/NunkiApplication.java): Main Spring Boot application entry point.
* [PingController.java](file:///home/vortigern/git/nunki/src/main/java/com/example/nunki/controller/PingController.java): Receives client websocket messages on `/app/ping` and replies to `/topic/ping-response`.
* [OpcUaController.java](file:///home/vortigern/git/nunki/src/main/java/com/example/nunki/controller/OpcUaController.java): Exposes REST HTTP API endpoints for browsing the address space, writing node values, and invoking method nodes.
* [OpcUaWiringService.java](file:///home/vortigern/git/nunki/src/main/java/com/example/nunki/opcua/service/OpcUaWiringService.java): Coordinates connections, browses tree on startup, registers subscription monitors on variable nodes, and forwards updates to `/topic/opcua-tree` via the STOMP template.
* [OpcUaClientService.java](file:///home/vortigern/git/nunki/src/main/java/com/example/nunki/opcua/service/OpcUaClientService.java): Wraps the Eclipse Milo OPC UA client implementation (browse, read, write, call).
* [ConnectionManager.java](file:///home/vortigern/git/nunki/src/main/java/com/example/nunki/opcua/connection/ConnectionManager.java): Configures and monitors connection handshakes.
* [SubscriptionManager.java](file:///home/vortigern/git/nunki/src/main/java/com/example/nunki/opcua/subscription/SubscriptionManager.java): Manages active Milo subscription monitors.
* [RequestQueue.java](file:///home/vortigern/git/nunki/src/main/java/com/example/nunki/opcua/queue/RequestQueue.java): Prevents race conditions by queuing concurrent write/call operations.

---

## 2. Component Design & Architectural Roles

### A. Bootstrapping Entry Point ([main.ts](file:///home/vortigern/git/nunki/src/main/frontend/src/main.ts))
* **Role**: Bootstraps Svelte 5.
* **Design & Logic**: Utilizes the modern `mount()` function of Svelte 5 to mount the root [App.svelte](file:///home/vortigern/git/nunki/src/main/frontend/src/App.svelte) component onto the HTML container (`#app`) using non-null assertion.

### B. Global Theme & Styling System ([app.css](file:///home/vortigern/git/nunki/src/main/frontend/src/app.css))
* **Role**: Visual design system definition.
* **Design & Logic**: Contains CSS variable tokens specifying color, typography, background gradients, and border-radius. Leverages `@media (prefers-color-scheme: dark)` to automate responsive dark-mode styling configurations.

### C. Application Router Shell ([App.svelte](file:///home/vortigern/git/nunki/src/main/frontend/src/App.svelte))
* **Role**: Main layout grid, reactive state shell, and page router.
* **Design & Logic**:
  * **Routing**: Evaluates `activeTab` to display the active view pane (`home`, `diagrams`, `timeseries`, `values-list`, `values-search`, `tree`, `automation-lua`).
  * **Networking Lifecycle**: Triggers `wsManager.connect()` inside `onMount` and disconnects sockets inside `onDestroy`.
  * **Modals Controller**: Manages modals (`login`, `logout`, `profile`) by modifying `userAction` state.

### D. WebSocket & STOMP Coordinator ([websocket.svelte.ts](file:///home/vortigern/git/nunki/src/main/frontend/src/lib/websocket.svelte.ts))
* **Role**: Full-stack async networking service.
* **Design & Logic**:
  * Encapsulates the `@stomp/stompjs` client connection to `ws://localhost:8080/ws-stomp` (dynamically compiled based on host port).
  * Exposes global reactive properties (`connected`, `pongs`, `opcUaUpdates`) to the rest of the application using Svelte 5 Runes.
  * Subscribes to STOMP destination channels:
    * `/topic/ping-response` - Caches and lists pongs, capping records at 50 logs.
    * `/topic/opcua-tree` - Map-updates incoming OPC-UA value shifts into the dictionary.
  * Provides async REST methods `writeOpcUaValue()` (POST to `/api/opcua/write`) and `invokeOpcUaMethod()` (POST to `/api/opcua/invoke`).

### E. Navigation dropdown Header ([Navbar.svelte](file:///home/vortigern/git/nunki/src/main/frontend/src/lib/Navbar.svelte))
* **Role**: Interactive Header navigation bar.
* **Design & Logic**:
  * Renders top header navigation controls with Lucide icons.
  * Registers a window event listener in `onMount` that intercepts window clicks. If a click target falls outside `.nav-item`, automatically collapses open dropdown submenus.

### F. Collapsible Hierarchical Node Navigator ([OpcUaTreeNode.svelte](file:///home/vortigern/git/nunki/src/main/frontend/src/lib/OpcUaTreeNode.svelte))
* **Role**: Recursive tree viewer for the OPC-UA address space.
* **Design & Logic**:
  * Recursively references itself for nested branches.
  * Translates node class states:
    * `Object` -> Collapsible directory.
    * `Variable` -> Renders live values, highlights updates, and offers inline value write triggers.
    * `Method` -> Lightning symbol invocation trigger and result display.
  * Triggers highlight flashes using an `$effect` block linked to shifts in the `liveValue` derived state.

### G. SVG Process Mimic Simulator ([SynopticView.svelte](file:///home/vortigern/git/nunki/src/main/frontend/src/lib/SynopticView.svelte))
* **Role**: Industrial human-machine interface (HMI).
* **Design & Logic**:
  * Renders inline SVGs of Storage Tank T-101, pipes, Feed Pump, and Drain Valve.
  * Connects an animation loop using `$effect` that shifts pipeline stroke dash offsets and rotates pump impeller blades when operational.
  * Binds Enter/Space keyboard event listeners to the valve graphical container for accessibility.

### H. SVG Telemetry Visualizer ([TimeSeriesChart.svelte](file:///home/vortigern/git/nunki/src/main/frontend/src/lib/TimeSeriesChart.svelte))
* **Role**: Real-time line chart plotting.
* **Design & Logic**:
  * Avoids heavy external charting libraries by scaling data points to pixel values within the SVG viewport using mathematical linear interpolation.
  * Dynamically re-scales axes by binding the container dimensions (`bind:clientWidth`, `bind:clientHeight`).

---

## 3. Svelte 5 Runes Reference

The Nunki frontend uses Svelte 5 Runes to achieve fine-grained reactivity:

1. **`$state(initialValue)`**: Declares reactive local variables. E.g., `let activeTab = $state('home')`.
2. **`$derived(expression)`**: Declares a reactive value that automatically updates when its dependencies change. E.g., `let liveValue = $derived(...)`.
3. **`$derived.by(fn)`**: Executes a multiline logic block to compute complex derived states. E.g., `let yGridLines = $derived.by(...)`.
4. **`$effect(fn)`**: Registers side effects. Automatically runs on mounting and re-triggers on dependency updates. E.g., setting animation interval timers.
5. **`$props()`**: Declares component parameters, replacing Svelte 4's `export let` syntax. E.g., `let { onSelect, currentTab } = $props()`.

---

## 4. Architectural Integration Diagrams

All diagrams below use clean, standard PlantUML syntax compatible with **PlantUML v1.2020.02** (avoiding newer styling commands or external imports that trigger parser warnings).

### A. Deployment Diagram
Shows the physical topology of the full-stack system, placing components within their respective logical tiers and runtime nodes.

```plantuml
@startuml
node "Client Machine (Web Browser)" {
    component "Svelte 5 App" {
        component "Navbar" as Nav
        component "SynopticView" as Synoptic
        component "TimeSeriesChart" as Chart
        component "OpcUaTreeNode" as TreeNode
        component "websocket.svelte.ts\n(STOMP Client)" as WSClient
    }
}

node "Spring Boot Application Server" {
    node "Tomcat Web Server" {
        component "Static UI Assets" as Assets
    }
    node "Spring Application Context" {
        component "PingController" as PingCtrl
        component "OpcUaController" as OpcUaCtrl
        component "OpcUaWiringService" as WiringSvc
        component "OpcUaClientService" as ClientSvc
        component "ConnectionManager" as ConnMgr
        component "RequestQueue" as ReqQueue
    }
    component "STOMP WebSocket Broker" as Broker
}

node "OPC-UA Server Host" {
    component "C++ Quasar OPC-UA Server\n(Port 4840)" as QuasarSrv
}

' Physical Node Interactions
WSClient <--> Broker : "WebSocket Connection\n(ws://localhost:8080/ws-stomp)"
Nav --> WSClient : "Query Status"
TreeNode --> WSClient : "Read / Write / Invoke"
Synoptic --> WSClient : "Read / Write"
Chart --> WSClient : "Subscribe updates"

OpcUaCtrl --> ClientSvc : "Browse / Write / Call"
PingCtrl --> Broker : "Route messages"
WiringSvc --> ClientSvc : "Start subscription monitors"
WiringSvc --> Broker : "Publish changes"

ClientSvc --> ConnMgr : "Get active Session"
ClientSvc --> ReqQueue : "Enqueue write / call"
ReqQueue --> ConnMgr : "Execute operations"
ConnMgr <--> QuasarSrv : "OPC-UA Binary protocol\n(TCP)"
@enduml
```

---

### B. Usecase Diagram
Describes the actions an Industrial Operator can perform via the Nunki dashboard and how they map to the system's interactive subsystems.

```plantuml
@startuml
left to right direction
actor "Industrial Operator" as User

rectangle "Nunki Control Panel" {
    usecase "Monitor Dashboard & Logs" as UC_Logs
    usecase "Send STOMP Ping (Manual)" as UC_Ping
    usecase "Toggle Auto Ping Loop" as UC_AutoPing
    usecase "View Process Synoptics (Mimic)" as UC_Synoptic
    usecase "Toggle Feed Pump" as UC_Pump
    usecase "Toggle Drain Valve" as UC_Valve
    usecase "Override Tank Level" as UC_Tank
    usecase "View Time-Series telemetry Chart" as UC_Chart
    usecase "Browse OPC-UA Address Space (Tree)" as UC_Tree
    usecase "Write Node value" as UC_Write
    usecase "Invoke OPC-UA Method" as UC_Invoke
    usecase "Write/Execute Lua Scripts" as UC_Lua
    usecase "Authenticate (Login/Logout/Profile)" as UC_Auth
}

User --> UC_Logs
User --> UC_Ping
User --> UC_AutoPing
User --> UC_Synoptic
User --> UC_Pump
User --> UC_Valve
User --> UC_Tank
User --> UC_Chart
User --> UC_Tree
User --> UC_Write
User --> UC_Invoke
User --> UC_Lua
User --> UC_Auth

UC_Pump .> UC_Synoptic : <<extends>>
UC_Valve .> UC_Synoptic : <<extends>>
UC_Tank .> UC_Synoptic : <<extends>>
UC_Write .> UC_Tree : <<extends>>
UC_Invoke .> UC_Tree : <<extends>>
@enduml
```

---

## 5. Interaction Sequence & Activity Diagrams

Below are the detailed sequence and activity diagrams illustrating the core interaction scenarios.

### A. WebSocket Connection & Live Telemetry Monitoring
Handles dashboard mounting, establishing WebSocket and STOMP connections, and forwarding real-time OPC-UA updates back to the UI.

#### Sequence Diagram:
```plantuml
@startuml
actor User
boundary "Svelte Frontend (UI)" as UI
control "websocket.svelte.ts\n(STOMP Client)" as WS
control "OpcUaWiringService\n(Spring Boot)" as Wiring
control "OpcUaClientService\n(Milo Client)" as Milo
entity "Quasar OPC-UA Server\n(C++)" as Quasar

== Connection Setup ==
User -> UI : Mount Dashboard
UI -> WS : wsManager.connect()
WS -> Wiring : Establish WebSocket connection & STOMP Handshake
WS -> Wiring : Subscribe to /topic/opcua-tree
Wiring -> Milo : Setup subscriptions for Variables

== Live Telemetry Update ==
Quasar -> Milo : Value Change Notification
Milo -> Wiring : Trigger Subscription Callback
Wiring -> WS : Broadcast JSON UpdateMessage to /topic/opcua-tree
WS -> UI : Update wsManager.opcUaUpdates state
UI -> User : Render new value with neon flash highlight
@enduml
```

#### Activity Diagram:
```plantuml
@startuml
start
:Mount Svelte Dashboard;
:Initiate STOMP client connection;
if (Connection Successful?) then (yes)
  :Subscribe to STOMP topics;
  :Launch OPC-UA subscription monitoring;
  fork
    :Await live telemetry update;
    :Milo Client receives tag change;
    :Forward update via STOMP message broker;
    :Parse JSON in websocket.svelte.ts;
    :Update reactive state ($state rune);
    :Trigger UI re-render and neon flash;
  fork end
else (no)
  :Retry connection in 5 seconds;
endif
stop
@enduml
```

---

### B. Writing Variable Node Value
Handles modifications to live OPC-UA variables initiated by the user.

#### Sequence Diagram:
```plantuml
@startuml
actor User
boundary "OpcUaTreeNode.svelte" as UI
control "websocket.svelte.ts" as WS
control "OpcUaController" as Ctrl
control "OpcUaClientService" as Milo
entity "Quasar OPC-UA Server" as Quasar

User -> UI : Enter new value and click Save
UI -> UI : inferType(value, name)
UI -> WS : wsManager.writeOpcUaValue(nodeId, value, type)
WS -> Ctrl : HTTP POST /api/opcua/write (JSON)
Ctrl -> Milo : write(nodeId, Variant)
Milo -> Quasar : Write Request
Quasar --> Milo : Write Response (StatusCode)
Milo --> Ctrl : Return StatusCode
Ctrl --> WS : Return Success boolean (StatusCode.isGood())
WS --> UI : Return success result
alt Success
  UI -> UI : Exit edit mode (isEditing = false)
else Failure
  UI -> User : Show write failure alert
end
@enduml
```

#### Activity Diagram:
```plantuml
@startuml
start
:User edits Variable node;
:Infer data type based on name and value string;
:Send HTTP POST to /api/opcua/write;
:Backend receives request and parses payload;
:Convert value string to Milo Variant (Boolean/Integer/Double/String);
:Execute Milo Client write to OPC-UA node;
if (Status code is Good?) then (yes)
  :Return success: true;
  :Frontend exits edit mode;
else (no)
  :Return success: false;
  :Frontend alerts user of failure;
endif
stop
@enduml
```

---

### C. Invoking Method Node
Handles method executions on the remote OPC-UA server.

#### Sequence Diagram:
```plantuml
@startuml
actor User
boundary "OpcUaTreeNode.svelte" as UI
control "websocket.svelte.ts" as WS
control "OpcUaController" as Ctrl
control "OpcUaClientService" as Milo
entity "Quasar OPC-UA Server" as Quasar

User -> UI : Click Invoke on Method node
UI -> WS : wsManager.invokeOpcUaMethod(objectId, methodId, args)
WS -> Ctrl : HTTP POST /api/opcua/invoke (JSON)
Ctrl -> Milo : call(objectId, methodId, variants)
Milo -> Quasar : Call Method Request
Quasar --> Milo : Call Method Response (Output values, StatusCode)
Milo --> Ctrl : Return CallMethodResult
Ctrl --> WS : Return Success & string result
WS --> UI : Return success status & result string
UI -> User : Display output result in badge
@enduml
```

#### Activity Diagram:
```plantuml
@startuml
start
:User clicks Invoke Method;
:Send HTTP POST to /api/opcua/invoke;
:Backend maps inputs to Variant arguments;
:Milo executes method call on OPC-UA server;
:C++ Quasar server runs internal logic;
:Return status and output arguments;
if (Invocation Successful?) then (yes)
  :Extract first output argument value;
  :Return success: true and result string;
  :Frontend shows success badge with returned value;
else (no)
  :Return success: false;
  :Frontend shows failure badge;
endif
stop
@enduml
```

---

### D. Sending WebSocket/STOMP Ping
Coordinates network connectivity checks.

#### Sequence Diagram:
```plantuml
@startuml
actor User
boundary "App.svelte" as UI
control "websocket.svelte.ts" as WS
control "PingController" as Ctrl
entity "STOMP Broker" as Broker

User -> UI : Click Send Ping (or 2s Auto Ping triggers)
UI -> WS : wsManager.sendPing(text)
WS -> Broker : STOMP SEND /app/ping (JSON payload)
Broker -> Ctrl : handlePing(PingMessage)
Ctrl -> Ctrl : Precondition validations & log message
Ctrl -> Broker : STOMP SEND /topic/ping-response (PongMessage JSON)
Broker -> WS : Push message to subscribers
WS -> WS : Parse JSON and prepend to pongs list (cap to 50)
WS --> UI : Reactive update (wsManager.pongs)
UI -> User : Render new pong entry in Response Logs console
@enduml
```

#### Activity Diagram:
```plantuml
@startuml
start
:User enters ping text;
:Click Send Ping or trigger Auto Ping;
:websocket.svelte.ts constructs JSON payload;
:Publish STOMP frame to /app/ping;
:Spring PingController intercepts message;
:Validate message parameters;
:Log message content and timestamp;
:Construct PongMessage with response text and current time;
:Publish STOMP message to /topic/ping-response;
:STOMP Client receives incoming PongMessage;
:Parse JSON payload;
:Prepend new pong record to local state list;
:If list size > 50, truncate oldest records;
:UI dynamically lists new pong item;
stop
@enduml
```

---

## 6. UI Wireframe & Component Instantiation Diagram

This layout diagram shows how Svelte components are instantiated and visually organized inside the Nunki Dashboard HMI shell.

```plantuml
@startuml
rectangle "App.svelte Layout" {
    rectangle "Navbar.svelte (Sticky Top Header)" as Nav {
        rectangle "Logo (N Nunki Dashboard)" as Logo
        rectangle "Workbenches Dropdown" as WBDrop {
            rectangle "Logs (home) | Charts (timeseries) | Synoptics (diagrams) | Values (List/Search/Tree) | Automation (Lua)"
        }
        rectangle "User Profile Dropdown" as UserDrop {
            rectangle "Log In | Log Out | Profile"
        }
    }
    
    rectangle "main.main-content (Active tab container)" as Content {
        
        alt activeTab === 'home'
            rectangle "Dashboard Header (Title & Subtitle)"
            rectangle "Grid Layout (2 Columns)" {
                rectangle "Ping Card (glass-card)" {
                    rectangle "Manual input field"
                    rectangle "Send Ping button"
                    rectangle "Start/Stop 2s Auto Ping button"
                }
                rectangle "Response Logs Card (glass-card)" {
                    rectangle "Live / Offline Status Badge"
                    rectangle "Scrollable list of Pong items (Time - Message)"
                }
            }
            
        else activeTab === 'diagrams'
            rectangle "Dashboard Header"
            rectangle "SynopticView.svelte (Interactive Process Mimic)" as SynopticComp {
                rectangle "Controls Area: [Start/Stop Pump] | [Open/Close Valve] | Tank Level: X% [Slider]"
                rectangle "SVG Canvas: Storage Tank T-101 | Pipes | Pump Impeller | Drain Valve Icon"
            }
            
        else activeTab === 'timeseries'
            rectangle "Dashboard Header"
            rectangle "TimeSeriesChart.svelte (Live Charting)" as ChartComp {
                rectangle "Chart Header: Point count indicator"
                rectangle "SVG Area: Dynamic grid lines | Area gradient | Plotted sensor line | Highlight dots"
            }
            
        else activeTab === 'tree'
            rectangle "Dashboard Header"
            rectangle "OPC-UA Address Space Container" {
                rectangle "Controls: [Refresh Address Space] | Error Message banner"
                rectangle "OpcUaTreeNode.svelte (Root Node - Recursive)" as TreeComp {
                    rectangle "Folder Node: 📁 Name (nodeId) [Chevron]"
                    rectangle "Child nodes (indented, dashed border left):" {
                        rectangle "Variable Node: 🔢 Name (nodeId) = LiveValue [Edit Button]"
                        rectangle "Method Node: ⚡ Name() (nodeId) [Invoke Button] [Result Badge]"
                    }
                }
            }
            
        else activeTab === 'automation-lua'
            rectangle "Dashboard Header"
            rectangle "Grid Layout (2 Columns)" {
                rectangle "Script Editor Card" {
                    rectangle "Lua code input (textarea)"
                    rectangle "Execute Script button"
                }
                rectangle "Execution Console Card" {
                    rectangle "Console log history list (stdout)"
                }
            }
        end
    }
}
@enduml
```

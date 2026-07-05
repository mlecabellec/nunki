# Svelte 5 Frontend Architecture & Logic Documentation

This document describes the design, directory structure, component details, and reactive state management logic of the Svelte 5 frontend interface in the **Nunki Control Panel** project.

---

## 1. Directory Structure

The Svelte frontend is situated in the directory `src/main/frontend` inside the project. The hierarchy of source files is organized as follows:

```
src/main/frontend/
├── index.html                  # Core HTML file containing root mount div#app
├── package.json                # Project dependencies and build scripts
├── svelte.config.js            # Configuration settings for Svelte 5
├── tsconfig.json               # Shared compiler configurations
├── vite.config.ts              # Vite configuration setting up dev environment
└── src/
    ├── main.ts                 # Application bootstrapping entrypoint
    ├── app.css                 # Global styles, variables, light/dark themes
    ├── App.svelte              # Main application shell & router
    └── lib/
        ├── Counter.svelte      # Basic counter template helper
        ├── Navbar.svelte       # Top navigation header dropdown bar
        ├── OpcUaTreeNode.svelte# Collapsible recursive OPC-UA tree nodes
        ├── SynopticView.svelte # SVG pipe fluid mimic diagram simulator
        ├── TimeSeriesChart.svelte # Native SVG line plot chart
        └── websocket.svelte.ts # STOMP client manager and shared states
```

---

## 2. Component Analysis & Logic Details

### A. Bootstrapping Entry Point (`main.ts`)
* **File path**: [src/main/frontend/src/main.ts](file:///home/vortigern/git/nunki/src/main/frontend/src/main.ts)
* **Design & Logic**: Utilizes the modern Svelte 5 `mount()` function to bind the root `App.svelte` layout directly to the element with ID `app` in `index.html`. It asserts the container target exists using the non-null assertion operator `!`, avoiding initial mounting null runtime exceptions.

### B. Global Styling & Responsive Variable Tokens (`app.css`)
* **File path**: [src/main/frontend/src/app.css](file:///home/vortigern/git/nunki/src/main/frontend/src/app.css)
* **Design & Logic**: Declares CSS variable tokens that adjust automatically based on user layout preferences (`@media (prefers-color-scheme: dark)`). Integrates custom styling parameters:
  * `--bg`: Page background.
  * `--text`: Paragraph layout description colors.
  * `--accent`: Primary highlights (violet violet gradients).
  * `--border`: Container outlines and margins.
  * Defines custom typography using standard browser defaults (`system-ui`, `Segoe UI`, `monospace`) mapped inside `--sans`, `--heading`, and `--mono`.

### C. Application Router Shell (`App.svelte`)
* **File path**: [src/main/frontend/src/App.svelte](file:///home/vortigern/git/nunki/src/main/frontend/src/App.svelte)
* **Design & Logic**:
  1. **Routing and View Modes**: Maps an active navigation string `activeTab` to display corresponding layouts:
     * `home`: Renders manual ping text boxes, STOMP client auto-pinger toggles, and response list logs.
     * `diagrams`: Visualizes `SynopticView`.
     * `timeseries`: Plugs local frequency caches into `TimeSeriesChart`.
     * `values-list`: Tabulates simulated variable telemetry (NodeID, Name, Value, DataType, Status).
     * `values-search`: Combines search values with tag keys through filters.
     * `tree`: Interfaces a dynamic address space recursively via `OpcUaTreeNode`.
     * `automation-lua`: Simulates Lua code execution loops, mapping standard register checks.
  2. **WebSocket Lifecycle hooks**: Connects the STOMP sockets manager (`wsManager.connect()`) inside Svelte's `onMount` and tears down intervals and sockets during `onDestroy`.
  3. **Simulated State Effect**: Features a Svelte 5 `$effect` monitoring incoming WebSocket responses. If a new ping arrives, computes simulated system telemetry, updates local arrays, and pushes random metrics into chart grids.
  4. **Modals Controller**: Manages login/logout overlays by capturing event clicks.

### D. WebSocket & STOMP Networking Manager (`websocket.svelte.ts`)
* **File path**: [src/main/frontend/src/lib/websocket.svelte.ts](file:///home/vortigern/git/nunki/src/main/frontend/src/lib/websocket.svelte.ts)
* **Design & Logic**:
  1. Encapsulates `@stomp/stompjs` client connectivity. Employs Svelte 5 `$state` runes on properties `connected` (boolean), `pongs` (array), and `opcUaUpdates` (key-value dictionary).
  2. Resolves CORS port redirects when running under dev HMR servers (ports `5173` vs `8080`).
  3. Establishes channel subscriptions once the socket connects:
     * `/topic/ping-response`: Appends pongs, capping records at 50 logs.
     * `/topic/opcua-tree`: Extracts live OPC-UA modifications, updating telemetry dictionary values.
  4. Dispatches asynchronous REST calls:
     * `writeOpcUaValue(nodeId, value, type)`: POSTs to `/api/opcua/write`.
     * `invokeOpcUaMethod(objectId, methodId, args)`: POSTs to `/api/opcua/invoke`.

### E. Navigation Header dropdowns (`Navbar.svelte`)
* **File path**: [src/main/frontend/src/lib/Navbar.svelte](file:///home/vortigern/git/nunki/src/main/frontend/src/lib/Navbar.svelte)
* **Design & Logic**:
  1. Standardizes app categories under "Workbenches" and "User" dropdown menus.
  2. Spawns an event listener in `onMount` that intercepts window clicks. If target is clicked outside `.nav-item` divs, collapses category arrays.
  3. Rotates chevron arrow vector graphics using reactive active CSS class bindings.

### F. Collapsible Hierarchical Node Navigator (`OpcUaTreeNode.svelte`)
* **File path**: [src/main/frontend/src/lib/OpcUaTreeNode.svelte](file:///home/vortigern/git/nunki/src/main/frontend/src/lib/OpcUaTreeNode.svelte)
* **Design & Logic**:
  1. Recursively mounts children branches to display the entire parsed OPC-UA address space (Objects as directories, Variables as values, Methods as execution buttons).
  2. Uses Svelte 5 `$derived` state to resolve node telemetry values against the WebSocket dictionary (`wsManager.opcUaUpdates`).
  3. Controls neon color highlights via `$effect` hooks. When values change, toggles a temporary state `flash`, triggering active CSS keyframe animations.
  4. Infers payload datatypes (Boolean, Integer, Double, String) depending on variable names and value structures.

### G. SVG Process mimic Simulator (`SynopticView.svelte`)
* **File path**: [src/main/frontend/src/lib/SynopticView.svelte](file:///home/vortigern/git/nunki/src/main/frontend/src/lib/SynopticView.svelte)
* **Design & Logic**:
  1. Simulates water storage vessels using SVG shapes: Storage Tank T-101 (200px width, 220px height, scale ticks), Feed Pump impeller blades, and Drain Valves.
  2. Configures a loop using `$effect` that:
     * Rotates impeller blades via CSS animations when pump is active.
     * Animates dashed pipeline strokes by incrementing `flowOffset` pixels.
     * Integrates level changes (inflowing fluid accumulates water level up to 95%; gravitational draining empties tank down to 10% dead volume).
  3. Captures Space/Enter key events on the valve group to guarantee keyboard accessibility.

### H. SVG Chart Plottings (`TimeSeriesChart.svelte`)
* **File path**: [src/main/frontend/src/lib/TimeSeriesChart.svelte](file:///home/vortigern/git/nunki/src/main/frontend/src/lib/TimeSeriesChart.svelte)
* **Design & Logic**:
  1. Calculates layout ranges (maxX, minX, maxY, minY) based on cached timestamps and frequency amplitudes using derived states.
  2. Employs linear interpolation math to map coordinates to pixel points inside the SVG drawing frame.
  3. Computes grid divider guide tracks via `$derived.by` block algorithms.
  4. Binds viewport sizes (`bind:clientWidth`, `bind:clientHeight`) to force immediate mathematical scaling on screen transitions.

---

## 3. Svelte 5 State Runes Reference

Svelte 5 replaces traditional compiler stores and `let` reactivity syntax with **Runes**. The frontend leverages these extensively:

1. **`$state(initialValue)`**:
   * *Purpose*: Declares a reactive variable.
   * *Example*: `let count = $state(0)` inside `Counter.svelte` ensures changes to `count` trigger re-renders.
2. **`$derived(expression)`**:
   * *Purpose*: Declares a reactive value computed from other states.
   * *Example*: `let liveValue = $derived(...)` inside `OpcUaTreeNode.svelte` automatically updates when `wsManager.opcUaUpdates` is modified.
3. **`$derived.by(fn)`**:
   * *Purpose*: Declares a derived value computed through procedural multiline blocks.
   * *Example*: `let yGridLines = $derived.by(...)` inside `TimeSeriesChart.svelte` computes list divisions.
4. **`$effect(fn)`**:
   * *Purpose*: Executes side-effects when dependencies alter. Replaces Svelte 4's reactive statements (`$: ...`).
   * *Example*: Registers anim intervals in `SynopticView.svelte` and cleans them up on transitions.
5. **`$props()`**:
   * *Purpose*: Declares component parameter inputs.
   * *Example*: `let { onSelect, currentTab } = $props()` inside `Navbar.svelte`.

---

## 4. Improvements & Compiler Issue Fixes

During the documentation audit, the following adjustments were made to ensure full functionality and type-safety:

1. **Self-Import Recursion Modernization**:
   * *Issue*: `OpcUaTreeNode.svelte` utilized Svelte's legacy `<svelte:self>` tag, which is deprecated in Svelte 5.
   * *Resolution*: Modernized recursive renders by importing the file directly and referencing the imported tag:
     ```typescript
     import OpcUaTreeNode from './OpcUaTreeNode.svelte';
     ```
     This cleans up compiler deprecation logs and ensures compatibility with Svelte 6.

2. **TypeScript Math Operation Type Casting**:
   * *Issue*: `nodeValues` elements were typed implicitly, causing `value` to be inferred as `string | number`. This triggered two compilation errors:
     * When adjusting `SystemLoad` values: `Error: Operator '+' cannot be applied to types 'string | number' and 'number'`.
     * In Lua script threshold tests: `Error: Operator '>' cannot be applied to types 'string | number' and 'number'`.
   * *Resolution*:
     * Declared an explicit `NodeValue` schema interface mapping `value: string | number`.
     * Cast `node.value` to number before mathematical modifications in the subscription effect:
       ```typescript
       const currentVal = typeof node.value === 'number' ? node.value : parseFloat(node.value as string);
       ```
     * Cast output values using `Number()` inside Lua sandbox triggers:
       ```typescript
       const targetInt = Number(nodeValues.find(n => n.name === 'MyInt')?.value ?? 42);
       ```

3. **Vite Build Output Warning Cleanup**:
   * *Issue*: The compiler output warnings for ten unused CSS selectors (`.tree-mockup`, `.tree-item`, etc.) inside `App.svelte` left over from manual tree mockups.
   * *Resolution*: Commented out legacy style mappings, removing compilation alerts and reducing production asset sizes.

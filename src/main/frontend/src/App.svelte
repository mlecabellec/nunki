<script lang="ts">
  /**
   * @file App.svelte
   * @description Main dashboard application shell for Nunki Control Panel.
   * Orchestrates the active views (logs, charting, diagrams, OPC-UA registers)
   * and houses mock models, Lua scripting emulation, and socket event callbacks.
   * 
   * Requirements:
   * - REQ-00014: WebSocket/STOMP async exchanges
   * - REQ-00021: Display dynamic SVG diagrams and synoptics
   * - REQ-00022: Display graph of time series
   * - REQ-00025: Support for authentication interface
   * - REQ-00026: Offline build support/clean navigation
   * - TSK-00031.1: Integrate Navbar in App
   * - TSK-00024.1: Premium Styling Redesign
   */

  import { onMount, onDestroy } from 'svelte';
  import { wsManager, getApiUrl } from './lib/websocket.svelte';
  import Navbar from './lib/Navbar.svelte';
  import TimeSeriesChart from './lib/TimeSeriesChart.svelte';
  import SynopticView from './lib/SynopticView.svelte';
  import OpcUaTreeNode from './lib/OpcUaTreeNode.svelte';
  
  // Import icons from lucide-svelte
  import { 
    Send, 
    Play, 
    Square,
    Search,
    PlayCircle,
    UserCheck,
    Cpu
  } from '@lucide/svelte';

  // --- Reactive States (Runes) ---
  
  // Currently active view tab ('home', 'diagrams', 'timeseries', 'values-list', 'values-search', 'tree', 'automation-lua')
  let activeTab = $state('home');
  
  // Tracks active account management modals ('login', 'logout', 'profile', or null)
  let userAction = $state<string | null>(null);
  
  // Username for mock login flow
  let loginUsername = $state('admin');
  
  // Password for mock login flow
  let loginPassword = $state('admin');
  
  // Simulated authentication state
  let isLoggedIn = $state(true);

  // Schema interface representing an OPC-UA tree structure
  interface OpcUaNode {
    nodeId: string;
    name: string;
    nodeClass: string;
    value: string;
    children: OpcUaNode[];
  }
  
  // Tree state populated from active OPC-UA backend endpoints
  let opcUaTree = $state<OpcUaNode | null>(null);
  
  // Flags whether a REST tree fetch request is actively running
  let isFetchingTree = $state(false);
  
  // Captures and displays OPC-UA tree connection error messages
  let treeFetchError = $state<string | null>(null);

  // Finds a NodeId by its path from the root, e.g., "Data/MyInt"
  function resolveNodeIdByPath(node: OpcUaNode | null, path: string): string | null {
    if (!node) return null;
    const segments = path.split('/');
    let current: OpcUaNode = node;
    
    for (const segment of segments) {
      let found: OpcUaNode | null = null;
      if (current.children) {
        for (const child of current.children) {
          if (child.name === segment) {
            found = child;
            break;
          }
        }
      }
      if (!found) return null;
      current = found;
    }
    return current.nodeId;
  }

  // Dictionary mapping paths to resolved numeric NodeIds
  let pathNodeIds = $derived.by(() => {
    const dict: Record<string, string> = {};
    if (opcUaTree) {
      const paths = [
        'Data/MyInt',
        'Data/MySwitch',
        'Data/PumpRunning',
        'Data/TankLevel',
        'CounterControl/CounterValue',
        'FastCounters/Counter_1Hz'
      ];
      for (const p of paths) {
        const id = resolveNodeIdByPath(opcUaTree, p);
        if (id) {
          dict[p] = id;
        }
      }
    }
    return dict;
  });

  /**
   * Dispatches GET request to fetch remote OPC-UA address space node hierarchies.
   */
  async function fetchOpcUaTree() {
    isFetchingTree = true;
    treeFetchError = null;
    try {
      const response = await fetch(getApiUrl('/api/opcua/tree'));
      if (!response.ok) {
        throw new Error('Server returned ' + response.status);
      }
      opcUaTree = await response.json();
    } catch (e: any) {
      console.error('Error fetching OPC-UA tree:', e);
      treeFetchError = e.message || 'Failed to fetch OPC-UA tree';
    } finally {
      isFetchingTree = false;
    }
  }

  // Value filter query for searching the address space
  let searchQuery = $state('');

  // Initial code template populated inside the Lua emulator text area
  let luaCode = $state(`-- Nunki Automation Script
local val = opcua.read("ns=1;s=Data/MyInt")
print("Current value of MyInt: " .. tostring(val))
if val > 40 then
    opcua.write("ns=1;s=Data/MySwitch", false)
    print("Switch turned OFF")
end`);
  
  // Logs stdout buffers rendered inside the Lua execution console
  let luaConsoleLogs = $state<string[]>([]);
  
  // Toggles loader spinner on script execution runtime
  let isRunningLua = $state(false);

  // Manual ping messaging input field content
  let pingText = $state('Ping Message');
  
  // Tracks active state of automated loop sending ping frames every 2 seconds
  let autoPingActive = $state(false);
  
  // Pointer reference to active setInterval instance for automated pings
  let autoPingIntervalId: any = null;

  // Chart telemetry cache
  interface ChartData {
    timestamp: number;
    value: number;
  }
  
  // Local reactive cache array plotted on the TimeSeries SVG chart
  // Local reactive cache array plotted on the TimeSeries SVG chart
  let timeSeriesData = $state<ChartData[]>([]);

  // Schema interface representing simulated table nodes
  interface NodeValue {
    id: string;
    name: string;
    value: string | number;
    type: string;
    status: string;
    ts: string;
  }

  // Derived registers populated reactively from active OPC-UA backend endpoints
  let nodeValues = $derived<NodeValue[]>([
    {
      id: pathNodeIds['Data/MyInt'] ?? 'ns=0;i=unknown',
      name: 'MyInt',
      value: pathNodeIds['Data/MyInt'] && wsManager.opcUaUpdates[pathNodeIds['Data/MyInt']]?.value !== undefined
        ? wsManager.opcUaUpdates[pathNodeIds['Data/MyInt']].value
        : '42',
      type: 'Int32',
      status: wsManager.connected && pathNodeIds['Data/MyInt'] ? 'Good' : 'Offline',
      ts: pathNodeIds['Data/MyInt'] && wsManager.opcUaUpdates[pathNodeIds['Data/MyInt']]?.timestamp 
        ? new Date(wsManager.opcUaUpdates[pathNodeIds['Data/MyInt']].timestamp).toLocaleTimeString()
        : new Date().toLocaleTimeString()
    },
    {
      id: pathNodeIds['Data/MySwitch'] ?? 'ns=0;i=unknown',
      name: 'MySwitch',
      value: pathNodeIds['Data/MySwitch'] && wsManager.opcUaUpdates[pathNodeIds['Data/MySwitch']]?.value !== undefined
        ? wsManager.opcUaUpdates[pathNodeIds['Data/MySwitch']].value
        : 'False',
      type: 'Boolean',
      status: wsManager.connected && pathNodeIds['Data/MySwitch'] ? 'Good' : 'Offline',
      ts: pathNodeIds['Data/MySwitch'] && wsManager.opcUaUpdates[pathNodeIds['Data/MySwitch']]?.timestamp 
        ? new Date(wsManager.opcUaUpdates[pathNodeIds['Data/MySwitch']].timestamp).toLocaleTimeString()
        : new Date().toLocaleTimeString()
    },
    {
      id: pathNodeIds['Data/PumpRunning'] ?? 'ns=0;i=unknown',
      name: 'PumpRunning',
      value: pathNodeIds['Data/PumpRunning'] && wsManager.opcUaUpdates[pathNodeIds['Data/PumpRunning']]?.value !== undefined
        ? wsManager.opcUaUpdates[pathNodeIds['Data/PumpRunning']].value
        : 'False',
      type: 'Boolean',
      status: wsManager.connected && pathNodeIds['Data/PumpRunning'] ? 'Good' : 'Offline',
      ts: pathNodeIds['Data/PumpRunning'] && wsManager.opcUaUpdates[pathNodeIds['Data/PumpRunning']]?.timestamp 
        ? new Date(wsManager.opcUaUpdates[pathNodeIds['Data/PumpRunning']].timestamp).toLocaleTimeString()
        : new Date().toLocaleTimeString()
    },
    {
      id: pathNodeIds['Data/TankLevel'] ?? 'ns=0;i=unknown',
      name: 'TankLevel',
      value: pathNodeIds['Data/TankLevel'] && wsManager.opcUaUpdates[pathNodeIds['Data/TankLevel']]?.value !== undefined
        ? parseFloat(wsManager.opcUaUpdates[pathNodeIds['Data/TankLevel']].value).toFixed(1)
        : '45.0',
      type: 'Double',
      status: wsManager.connected && pathNodeIds['Data/TankLevel'] ? 'Good' : 'Offline',
      ts: pathNodeIds['Data/TankLevel'] && wsManager.opcUaUpdates[pathNodeIds['Data/TankLevel']]?.timestamp 
        ? new Date(wsManager.opcUaUpdates[pathNodeIds['Data/TankLevel']].timestamp).toLocaleTimeString()
        : new Date().toLocaleTimeString()
    },
    {
      id: pathNodeIds['CounterControl/CounterValue'] ?? 'ns=0;i=unknown',
      name: 'CounterValue',
      value: pathNodeIds['CounterControl/CounterValue'] && wsManager.opcUaUpdates[pathNodeIds['CounterControl/CounterValue']]?.value !== undefined
        ? wsManager.opcUaUpdates[pathNodeIds['CounterControl/CounterValue']].value
        : '0',
      type: 'Int32',
      status: wsManager.connected && pathNodeIds['CounterControl/CounterValue'] ? 'Good' : 'Offline',
      ts: pathNodeIds['CounterControl/CounterValue'] && wsManager.opcUaUpdates[pathNodeIds['CounterControl/CounterValue']]?.timestamp 
        ? new Date(wsManager.opcUaUpdates[pathNodeIds['CounterControl/CounterValue']].timestamp).toLocaleTimeString()
        : new Date().toLocaleTimeString()
    },
    {
      id: pathNodeIds['FastCounters/Counter_1Hz'] ?? 'ns=0;i=unknown',
      name: 'Counter_1Hz',
      value: pathNodeIds['FastCounters/Counter_1Hz'] && wsManager.opcUaUpdates[pathNodeIds['FastCounters/Counter_1Hz']]?.value !== undefined
        ? wsManager.opcUaUpdates[pathNodeIds['FastCounters/Counter_1Hz']].value
        : '0',
      type: 'Int32',
      status: wsManager.connected && pathNodeIds['FastCounters/Counter_1Hz'] ? 'Good' : 'Offline',
      ts: pathNodeIds['FastCounters/Counter_1Hz'] && wsManager.opcUaUpdates[pathNodeIds['FastCounters/Counter_1Hz']]?.timestamp 
        ? new Date(wsManager.opcUaUpdates[pathNodeIds['FastCounters/Counter_1Hz']].timestamp).toLocaleTimeString()
        : new Date().toLocaleTimeString()
    }
  ]);

  // Connect STOMP websockets on layout mount
  onMount(() => {
    wsManager.connect();
    fetchOpcUaTree();
  });

  // Stop background intervals and disconnect sockets on layout destroy
  onDestroy(() => {
    stopAutoPing();
    wsManager.disconnect();
  });

  /**
   * Svelte 5 Effect block plotting live Counter_1Hz updates onto TimeSeries graph.
   */
  $effect(() => {
    const counterId = pathNodeIds['FastCounters/Counter_1Hz'];
    if (!counterId) return;
    const update = wsManager.opcUaUpdates[counterId];
    if (update) {
      const timestamp = update.timestamp;
      const value = parseFloat(update.value);
      if (!isNaN(value)) {
        const exists = timeSeriesData.some(d => d.timestamp === timestamp);
        if (!exists) {
          timeSeriesData = [...timeSeriesData, { timestamp, value }].slice(-20);
        }
      }
    }
  });

  // Derived filter logic matching search strings with node keys
  let filteredNodes = $derived(
    nodeValues.filter(node => 
      node.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      node.id.toLowerCase().includes(searchQuery.toLowerCase())
    )
  );

  /**
   * Router selector callback linked to Navbar selections.
   */
  function handleNavbarSelect(tab: string) {
    if (['login', 'logout', 'profile'].includes(tab)) {
      userAction = tab;
    } else {
      activeTab = tab;
      if (tab === 'tree') {
        fetchOpcUaTree();
      }
    }
  }

  /**
   * Mock authenticator sign-in submission handler.
   */
  function handleLogin() {
    isLoggedIn = true;
    userAction = null;
  }

  /**
   * Mock authenticator sign-out handler.
   */
  function handleLogout() {
    isLoggedIn = false;
    userAction = null;
  }

  /**
   * Dispatches manual ping messages onto STOMP sockets.
   */
  function sendPing() {
    wsManager.sendPing(pingText);
  }

  /**
   * Toggles the automated ping loop timer.
   */
  function toggleAutoPing() {
    if (autoPingActive) {
      stopAutoPing();
    } else {
      startAutoPing();
    }
  }

  /**
   * Spawns interval timer dispatching ping messages.
   */
  function startAutoPing() {
    autoPingActive = true;
    autoPingIntervalId = setInterval(() => {
      const randomId = Math.floor(Math.random() * 1000);
      wsManager.sendPing(`Auto Ping #${randomId}`);
    }, 2000);
  }

  /**
   * Cleans up background interval loop timers.
   */
  function stopAutoPing() {
    autoPingActive = false;
    if (autoPingIntervalId) {
      clearInterval(autoPingIntervalId);
      autoPingIntervalId = null;
    }
  }

  /**
   * Simulates Lua sandbox engine runtime execution.
   * Feeds outputs directly onto console buffer display window.
   */
  function runLuaScript() {
    isRunningLua = true;
    luaConsoleLogs = ["Initializing Lua engine v5.4...", "Connecting to OPC-UA address space..."];
    setTimeout(() => {
      luaConsoleLogs = [...luaConsoleLogs, "Running automation script..."];
    }, 400);
    setTimeout(() => {
      // FIX: Cast string/number value to a reliable integer to resolve compiler operations
      const targetInt = Number(nodeValues.find(n => n.name === 'MyInt')?.value ?? 42);
      luaConsoleLogs = [
        ...luaConsoleLogs, 
        `[OPC-UA] Read value of 'ns=1;s=Data/MyInt' -> ${targetInt}`,
        `[Console] Current value of MyInt: ${targetInt}`,
        ...(targetInt > 40 ? [
          "[OPC-UA] Writing value 'false' to 'ns=1;s=Data/MySwitch'...",
          "[Console] Switch turned OFF"
        ] : [
          "[Console] Value is below threshold (40), no actions taken."
        ]),
        "Execution complete. (exit code: 0)"
      ];
      isRunningLua = false;
    }, 1000);
  }
</script>

<!-- 
  ==========================================
  --- CORE APPLICATION TEMPLATE LAYOUT ---
  ==========================================
  Main layout wrapper configuring the global navbar and container spacing.
-->
<div class="app-layout">
  <!-- Top navigation bar, dispatching selection events reactively to handleNavbarSelect -->
  <Navbar onSelect={handleNavbarSelect} currentTab={activeTab} />

  <!-- Scrollable main panel content area -->
  <main class="main-content">
    
    <!-- ==========================================
         --- PANEL VIEW A: HOME / LOGS VIEW ---
         ========================================== -->
    {#if activeTab === 'home'}
      <div class="dashboard-header">
        <h1>Nunki Control Panel</h1>
        <p>Real-time Industrial Monitoring & OPC-UA Client Interface</p>
      </div>

      <div class="grid-layout">
        <!-- WebSocket STOMP Test Deck Card -->
        <div class="glass-card ping-card">
          <h3>WebSocket / STOMP Ping Loop</h3>
          <p class="subtitle">Test async connectivity with Spring Boot backend</p>
          
          <!-- Manual ping string dispatcher -->
          <div class="input-group">
            <input type="text" bind:value={pingText} placeholder="Enter ping text" />
            <button onclick={sendPing} disabled={!wsManager.connected} class="btn-send">
              <Send size={16} />
              <span>Send Ping</span>
            </button>
          </div>

          <div class="divider"><span>OR</span></div>

          <!-- Automated background 2-second ping toggle button -->
          <button onclick={toggleAutoPing} class="btn-auto-ping" class:active={autoPingActive} disabled={!wsManager.connected}>
            {#if autoPingActive}
              <Square size={16} fill="white" />
              <span>Stop 2s Auto Ping</span>
            {:else}
              <Play size={16} fill="currentColor" />
              <span>Start 2s Auto Ping</span>
            {/if}
          </button>
        </div>

        <!-- WebSocket Response Logger Console Card -->
        <div class="glass-card logs-card">
          <div class="card-header">
            <h3>Response Logs</h3>
            <span class="badge" class:live={wsManager.connected}>
              {wsManager.connected ? 'LIVE' : 'OFFLINE'}
            </span>
          </div>
          
          <div class="logs-container">
            {#if wsManager.pongs.length === 0}
              <div class="empty-logs">
                <p>No messages received yet. Send a ping to start.</p>
              </div>
            {:else}
              <div class="logs-list">
                <!-- Renders historical WebSocket messages sorted chronologically -->
                {#each wsManager.pongs as pong (pong.id)}
                  <div class="log-item">
                    <span class="log-time">{new Date(pong.timestamp).toLocaleTimeString()}</span>
                    <span class="log-msg">{pong.message}</span>
                  </div>
                {/each}
              </div>
            {/if}
          </div>
        </div>
      </div>

    <!-- ==========================================
         --- PANEL VIEW B: PROCESS MIMIC DIAGRAMS ---
         ========================================== -->
    {:else if activeTab === 'diagrams'}
      <div class="dashboard-header">
        <h1>Process Synoptics</h1>
        <p>Interactive process flow diagrams linked with dynamic sensor states</p>
      </div>
      <div class="glass-card full-card">
        <!-- Dynamic inline SVG component simulating water vessel levels -->
        <SynopticView 
          pumpRunningNodeId={pathNodeIds['Data/PumpRunning']}
          valveOpenNodeId={pathNodeIds['Data/MySwitch']}
          tankLevelNodeId={pathNodeIds['Data/TankLevel']}
        />
      </div>

    <!-- ==========================================
         --- PANEL VIEW C: TIME TRENDING CHARTS ---
         ========================================== -->
    {:else if activeTab === 'timeseries'}
      <div class="dashboard-header">
        <h1>Time Series Visualizer</h1>
        <p>Real-time graph plotted directly via SVG based on WebSocket events</p>
      </div>
      <div class="glass-card full-card chart-view-card">
        <div class="chart-header">
          <h3>Simulated Sensor Frequency (Hz)</h3>
          <span class="chart-points-count">{timeSeriesData.length} data points cached</span>
        </div>
        <!-- SVG plotting component drawing raw points coordinate arrays -->
        <TimeSeriesChart data={timeSeriesData} />
      </div>

    <!-- ==========================================
         --- PANEL VIEW D: TELEMETRY VALUES LIST ---
         ========================================== -->
    {:else if activeTab === 'values-list'}
      <div class="dashboard-header">
        <h1>OPC-UA Node values</h1>
        <p>List view of variables and attributes in the address space</p>
      </div>
      <div class="glass-card full-card">
        <table class="data-table">
          <thead>
            <tr>
              <th>Node ID</th>
              <th>Name</th>
              <th>Value</th>
              <th>Data Type</th>
              <th>Status</th>
              <th>Last Updated</th>
            </tr>
          </thead>
          <tbody>
            <!-- Simple tag matrix listing mock values -->
            {#each nodeValues as node}
              <tr>
                <td class="mono">{node.id}</td>
                <td class="bold">{node.name}</td>
                <td class="mono accent-color">{node.value}</td>
                <td><span class="type-badge">{node.type}</span></td>
                <td><span class="status-ok">● Good</span></td>
                <td class="mono">{node.ts}</td>
              </tr>
            {/each}
          </tbody>
        </table>
      </div>

    <!-- ==========================================
         --- PANEL VIEW E: REGISTERS SEARCH ENGINE ---
         ========================================== -->
    {:else if activeTab === 'values-search'}
      <div class="dashboard-header">
        <h1>Search Address Space</h1>
        <p>Query nodes dynamically across all active registers</p>
      </div>
      <div class="glass-card full-card">
        <!-- Input field dynamically filtering derived collection filteredNodes -->
        <div class="search-bar-container">
          <Search size={18} class="search-icon" />
          <input type="text" placeholder="Search by Node ID or Name (e.g. MyInt)" bind:value={searchQuery} />
        </div>

        <table class="data-table">
          <thead>
            <tr>
              <th>Node ID</th>
              <th>Name</th>
              <th>Value</th>
              <th>Data Type</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {#if filteredNodes.length === 0}
              <tr>
                <td colspan="5" style="text-align: center; color: var(--text);">No matching nodes found.</td>
              </tr>
            {:else}
              {#each filteredNodes as node}
                <tr>
                  <td class="mono">{node.id}</td>
                  <td class="bold">{node.name}</td>
                  <td class="mono accent-color">{node.value}</td>
                  <td><span class="type-badge">{node.type}</span></td>
                  <td><span class="status-ok">● Good</span></td>
                </tr>
              {/each}
            {/if}
          </tbody>
        </table>
      </div>

    <!-- ==========================================
         --- PANEL VIEW F: RECURSIVE HIERARCHICAL TREE ---
         ========================================== -->
    {:else if activeTab === 'tree'}
      <div class="dashboard-header">
        <h1>OPC-UA Address Space</h1>
        <p>Interactive view of active nodes, folders and attributes</p>
      </div>
      <div class="glass-card full-card">
        <!-- Control strip offering address space re-scanning actions -->
        <div class="tree-controls" style="margin-bottom: 16px; display: flex; gap: 8px; align-items: center;">
          <button class="btn-action" onclick={fetchOpcUaTree} disabled={isFetchingTree} style="padding: 6px 16px; font-size: 14px; border-radius: 6px; cursor: pointer; border: 1px solid var(--accent); background: var(--accent-bg); color: var(--accent); transition: all 0.2s;">
            {isFetchingTree ? 'Refreshing...' : 'Refresh Address Space'}
          </button>
          {#if treeFetchError}
            <span style="color: #ef4444; font-size: 14px;">Error: {treeFetchError}</span>
          {/if}
        </div>

        {#if isFetchingTree && !opcUaTree}
          <p style="text-align: center; padding: 20px; color: var(--text);">Loading address space from OPC-UA server...</p>
        {:else if opcUaTree}
          <!-- Displays root tree node which recursively calls children instances -->
          <div class="tree-display" style="padding: 10px; max-height: 70vh; overflow-y: auto;">
            <OpcUaTreeNode node={opcUaTree} />
          </div>
        {:else}
          <p style="text-align: center; padding: 20px; color: #ef4444;">
            Failed to connect to OPC-UA backend. {treeFetchError || 'Check that the Quasar server is running.'}
          </p>
        {/if}
      </div>

    <!-- ==========================================
         --- PANEL VIEW G: LUA SCRIPTS DECK ---
         ========================================== -->
    {:else if activeTab === 'automation-lua'}
      <div class="dashboard-header">
        <h1>Lua Automation Scripting</h1>
        <p>Design event-driven scripts triggered on OPC-UA change subscriptions</p>
      </div>
      <div class="grid-layout">
        <!-- Editor text container -->
        <div class="glass-card">
          <div class="card-header">
            <h3>Script Editor</h3>
            <button class="btn-run" onclick={runLuaScript} disabled={isRunningLua}>
              <PlayCircle size={16} />
              <span>{isRunningLua ? 'Running...' : 'Execute Script'}</span>
            </button>
          </div>
          <textarea class="code-editor" bind:value={luaCode} rows="10"></textarea>
        </div>

        <!-- Terminal log display simulating stdout/stderr buffers -->
        <div class="glass-card console-card">
          <h3>Execution Console</h3>
          <div class="console-box">
            {#if luaConsoleLogs.length === 0}
              <div class="empty-logs">
                <Cpu size={24} style="margin-bottom: 8px; opacity: 0.5;" />
                <p>Console output is idle. Execute the script to view logs.</p>
              </div>
            {:else}
              {#each luaConsoleLogs as log}
                <div class="console-line">{log}</div>
              {/each}
            {/if}
          </div>
        </div>
      </div>
    {/if}
  </main>
</div>

<!-- ==========================================
     --- DIALOG POPUP MODAL BACKDROPS ---
     ========================================== -->
{#if userAction}
  <!-- Backing shadow. Closes modal on ESC keypress or clicking backdrop -->
  <div class="modal-backdrop" 
       onclick={(e) => { if (e.target === e.currentTarget) userAction = null; }} 
       onkeydown={(e) => { if (e.key === 'Escape') userAction = null; }}
       role="button"
       tabindex="-1"
       aria-label="Close modal">
    <div class="modal-content">
      
      <!-- Login modal dialogue -->
      {#if userAction === 'login'}
        <h3>Log In to Nunki</h3>
        <div class="form-group">
          <label for="login-username">Username</label>
          <input id="login-username" type="text" bind:value={loginUsername} />
        </div>
        <div class="form-group">
          <label for="login-password">Password</label>
          <input id="login-password" type="password" bind:value={loginPassword} />
        </div>
        <div class="modal-actions">
          <button class="btn-cancel" onclick={() => userAction = null}>Cancel</button>
          <button class="btn-primary" onclick={handleLogin}>Log In</button>
        </div>
      
      <!-- Logout confirmation modal dialogue -->
      {:else if userAction === 'logout'}
        <h3>Confirm Log Out</h3>
        <p>Are you sure you want to log out of the control panel?</p>
        <div class="modal-actions">
          <button class="btn-cancel" onclick={() => userAction = null}>Cancel</button>
          <button class="btn-danger" onclick={handleLogout}>Log Out</button>
        </div>
      
      <!-- User profile details dialogue -->
      {:else if userAction === 'profile'}
        <div class="profile-header">
          <UserCheck size={32} class="profile-icon" />
          <h3>User Profile</h3>
        </div>
        <div class="profile-info">
          <p><strong>Status:</strong> {isLoggedIn ? 'Authenticated' : 'Guest'}</p>
          {#if isLoggedIn}
            <p><strong>Role:</strong> Administrator</p>
            <p><strong>Username:</strong> {loginUsername}</p>
          {/if}
        </div>
        <div class="modal-actions">
          <button class="btn-primary" onclick={() => userAction = null}>Close</button>
        </div>
      {/if}
    </div>
  </div>
{/if}

<style>
  :global(body) {
    margin: 0;
    padding: 0;
    background-color: var(--bg);
    color: var(--text);
    overflow: hidden;
  }

  .app-layout {
    display: flex;
    flex-direction: column;
    height: 100vh;
    width: 100vw;
    font-family: system-ui, -apple-system, sans-serif;
  }

  .main-content {
    flex-grow: 1;
    padding: 30px 40px;
    overflow-y: auto;
    box-sizing: border-box;
    display: flex;
    flex-direction: column;
    gap: 24px;
    max-width: 1400px;
    margin: 0 auto;
    width: 100%;
  }

  .dashboard-header {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .dashboard-header h1 {
    margin: 0;
    font-size: 28px;
    font-weight: 600;
    color: var(--text-h);
  }

  .dashboard-header p {
    margin: 0;
    color: var(--text);
    font-size: 14px;
  }

  .grid-layout {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 24px;
  }

  .glass-card {
    background: var(--bg);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 20px;
    box-shadow: var(--shadow);
    display: flex;
    flex-direction: column;
    gap: 16px;
    position: relative;
    overflow: hidden;
  }

  .glass-card::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 4px;
    background: linear-gradient(90deg, var(--accent), transparent);
  }

  .glass-card h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: var(--text-h);
  }

  .subtitle {
    margin: 0;
    font-size: 13px;
    color: var(--text);
  }

  .input-group {
    display: flex;
    gap: 10px;
  }

  .input-group input {
    flex-grow: 1;
    padding: 8px 14px;
    border-radius: 6px;
    border: 1px solid var(--border);
    background: var(--code-bg);
    color: var(--text-h);
    font-size: 13px;
  }

  .input-group input:focus {
    outline: none;
    border-color: var(--accent);
  }

  .btn-send {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 16px;
    background: var(--accent);
    color: white;
    border: none;
    border-radius: 6px;
    font-weight: 500;
    cursor: pointer;
    transition: background-color 0.2s;
  }

  .btn-send:hover:not(:disabled) {
    background: #902fe5;
  }

  .btn-send:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .divider {
    display: flex;
    align-items: center;
    text-align: center;
    color: var(--text);
    font-size: 11px;
    font-weight: bold;
  }

  .divider::before,
  .divider::after {
    content: '';
    flex: 1;
    border-bottom: 1px solid var(--border);
  }

  .divider span {
    padding: 0 10px;
  }

  .btn-auto-ping {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    padding: 10px;
    background: var(--code-bg);
    border: 1px solid var(--border);
    border-radius: 6px;
    color: var(--text-h);
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s;
  }

  .btn-auto-ping:hover:not(:disabled) {
    border-color: var(--accent);
    color: var(--accent);
  }

  .btn-auto-ping.active {
    background: #e53e3e;
    color: white;
    border-color: #e53e3e;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .badge {
    padding: 4px 8px;
    border-radius: 20px;
    font-size: 10px;
    font-weight: bold;
    background: rgba(229, 62, 62, 0.1);
    color: #e53e3e;
  }

  .badge.live {
    background: rgba(72, 187, 120, 0.1);
    color: #48bb78;
    box-shadow: 0 0 10px rgba(72, 187, 120, 0.2);
  }

  .logs-container {
    flex-grow: 1;
    height: 180px;
    overflow-y: auto;
    border: 1px solid var(--border);
    border-radius: 6px;
    background: var(--code-bg);
    padding: 10px;
  }

  .empty-logs {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 100%;
    color: var(--text);
    font-size: 13px;
    text-align: center;
  }

  .logs-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .log-item {
    display: flex;
    gap: 12px;
    font-size: 12px;
    font-family: var(--mono);
    border-bottom: 1px solid rgba(0, 0, 0, 0.05);
    padding-bottom: 4px;
  }

  .log-time {
    color: var(--accent);
    font-weight: bold;
  }

  .log-msg {
    color: var(--text-h);
  }

  .full-card {
    flex-grow: 1;
    min-height: 480px;
  }

  .chart-view-card {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .chart-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .chart-points-count {
    font-size: 12px;
    font-family: var(--mono);
    color: var(--text);
  }

  /* Interactive Data Table styling */
  .data-table {
    width: 100%;
    border-collapse: collapse;
    margin-top: 10px;
  }

  .data-table th, .data-table td {
    padding: 12px;
    text-align: left;
    border-bottom: 1px solid var(--border);
  }

  .data-table th {
    font-size: 13px;
    color: var(--text);
    font-weight: 600;
    background: var(--code-bg);
  }

  .data-table td {
    font-size: 13px;
  }

  .mono {
    font-family: var(--mono);
  }

  .bold {
    font-weight: 600;
    color: var(--text-h);
  }

  .accent-color {
    color: var(--accent);
    font-weight: 600;
  }

  .type-badge {
    background: var(--code-bg);
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 11px;
    font-family: var(--mono);
  }

  .status-ok {
    color: #48bb78;
    font-weight: bold;
    font-size: 12px;
  }

  /* Search Bar */
  .search-bar-container {
    display: flex;
    align-items: center;
    gap: 10px;
    background: var(--code-bg);
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 8px 12px;
    margin-bottom: 16px;
  }

  .search-bar-container :global(svg) {
    opacity: 0.5;
  }

  .search-bar-container input {
    border: none;
    background: transparent;
    width: 100%;
    color: var(--text-h);
    font-size: 14px;
  }

  .search-bar-container input:focus {
    outline: none;
  }

  /* 
   * LEGACY TREE MOCKUP STYLING
   * Retained for reference/regression fallback but commented out to clean compiler output.
   *
  .tree-mockup {
    font-family: var(--mono);
    font-size: 13px;
    color: var(--text-h);
    padding: 10px;
  }

  .tree-item {
    margin: 6px 0;
  }

  .tree-children {
    margin-left: 24px;
    border-left: 1px dashed var(--border);
    padding-left: 12px;
  }

  .tree-item .name {
    font-weight: 500;
  }

  .tree-item .value {
    color: var(--accent);
    margin-left: 8px;
  }

  .tree-item .badge {
    margin-left: 6px;
    font-size: 10px;
    padding: 2px 6px;
    border-radius: 4px;
  }

  .tree-item .badge.read {
    background: rgba(49, 130, 206, 0.1);
    color: #3182ce;
  }

  .tree-item .badge.write {
    background: rgba(221, 107, 32, 0.1);
    color: #dd6b20;
  }

  .tree-item .badge.subscribe {
    background: rgba(128, 90, 213, 0.1);
    color: #805ad5;
  }
  */

  /* Lua scripting styles */
  .btn-run {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 12px;
    background: #48bb78;
    color: white;
    border: none;
    border-radius: 6px;
    font-size: 12px;
    font-weight: 500;
    cursor: pointer;
  }

  .btn-run:hover:not(:disabled) {
    background: #38a169;
  }

  .btn-run:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .code-editor {
    flex-grow: 1;
    font-family: var(--mono);
    font-size: 13px;
    background: var(--code-bg);
    color: var(--text-h);
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 12px;
    resize: none;
    line-height: 145%;
  }

  .code-editor:focus {
    outline: none;
    border-color: var(--accent);
  }

  .console-card {
    background: #0d0e12;
    border-color: #1a1c23;
  }

  .console-card::before {
    background: linear-gradient(90deg, #48bb78, transparent);
  }

  .console-card h3 {
    color: #f7fafc;
  }

  .console-box {
    flex-grow: 1;
    background: #07080a;
    border: 1px solid #1a1c23;
    border-radius: 6px;
    padding: 12px;
    overflow-y: auto;
    height: 250px;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .console-line {
    font-family: var(--mono);
    font-size: 12px;
    color: #a0aec0;
    line-height: 140%;
  }

  /* Modal Styles */
  .modal-backdrop {
    position: fixed;
    top: 0;
    left: 0;
    width: 100vw;
    height: 100vh;
    background: rgba(0, 0, 0, 0.5);
    backdrop-filter: blur(4px);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 1000;
  }

  .modal-content {
    background: var(--bg);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 24px;
    width: 100%;
    max-width: 400px;
    box-shadow: var(--shadow);
    display: flex;
    flex-direction: column;
    gap: 16px;
    animation: zoomIn 0.2s cubic-bezier(0.16, 1, 0.3, 1);
  }

  @keyframes zoomIn {
    from {
      opacity: 0;
      transform: scale(0.95);
    }
    to {
      opacity: 1;
      transform: scale(1);
    }
  }

  .modal-content h3 {
    margin: 0;
    font-size: 18px;
    color: var(--text-h);
  }

  .form-group {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .form-group label {
    font-size: 12px;
    font-weight: 600;
    color: var(--text);
  }

  .form-group input {
    padding: 8px 12px;
    border: 1px solid var(--border);
    border-radius: 6px;
    background: var(--code-bg);
    color: var(--text-h);
    font-size: 13px;
  }

  .form-group input:focus {
    outline: none;
    border-color: var(--accent);
  }

  .modal-actions {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    margin-top: 10px;
  }

  .modal-actions button {
    padding: 8px 16px;
    border-radius: 6px;
    border: 1px solid var(--border);
    background: var(--bg);
    color: var(--text);
    font-size: 13px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.15s;
  }

  .modal-actions .btn-primary {
    background: var(--accent);
    color: white;
    border: none;
  }

  .modal-actions .btn-primary:hover {
    background: #902fe5;
  }

  .modal-actions .btn-danger {
    background: #e53e3e;
    color: white;
    border: none;
  }

  .modal-actions .btn-danger:hover {
    background: #c53030;
  }

  .modal-actions .btn-cancel:hover {
    background: var(--code-bg);
  }

  .profile-header {
    display: flex;
    align-items: center;
    gap: 12px;
    color: var(--accent);
  }

  .profile-info {
    font-size: 14px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .profile-info p {
    margin: 0;
  }
</style>
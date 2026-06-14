<script lang="ts">
  /*
   * REQ-00014 – Support for websocket/STOMP async exchanges
   * REQ-00021 – ability to display dynamic SVG diagrams and synoptics
   * REQ-00022 – ability to display graph of time series
   * REQ-00025 – Support for authentication interface
   * REQ-00026 – Offline build support/clean navigation
   * TSK-00031.1 – Integrate Navbar in App
   * TSK-00024.1 – Premium Styling Redesign
   */

  import { onMount, onDestroy } from 'svelte';
  import { wsManager } from './lib/websocket.svelte';
  import Navbar from './lib/Navbar.svelte';
  import TimeSeriesChart from './lib/TimeSeriesChart.svelte';
  import SynopticView from './lib/SynopticView.svelte';
  
  // Lucide Icons
  import { 
    Send, 
    Play, 
    Square,
    Search,
    PlayCircle,
    UserCheck,
    Cpu
  } from '@lucide/svelte';

  let activeTab = $state('home');
  let userAction = $state<string | null>(null);
  let loginUsername = $state('admin');
  let loginPassword = $state('admin');
  let isLoggedIn = $state(true);

  // Search input state
  let searchQuery = $state('');

  // Lua Scripting state
  let luaCode = $state(`-- Nunki Automation Script
local val = opcua.read("ns=1;s=Data/MyInt")
print("Current value of MyInt: " .. tostring(val))
if val > 40 then
    opcua.write("ns=1;s=Data/MySwitch", false)
    print("Switch turned OFF")
end`);
  let luaConsoleLogs = $state<string[]>([]);
  let isRunningLua = $state(false);

  let pingText = $state('Ping Message');
  let autoPingActive = $state(false);
  let autoPingIntervalId: any = null;

  // Local chart data cache
  interface ChartData {
    timestamp: number;
    value: number;
  }
  let timeSeriesData = $state<ChartData[]>([
    { timestamp: Date.now() - 50000, value: 35 },
    { timestamp: Date.now() - 40000, value: 45 },
    { timestamp: Date.now() - 30000, value: 20 },
    { timestamp: Date.now() - 20000, value: 60 },
    { timestamp: Date.now() - 10000, value: 55 },
  ]);

  // OPC UA simulated node values
  let nodeValues = $state([
    { id: 'ns=1;s=Data/MyInt', name: 'MyInt', value: 42, type: 'Int32', status: 'Good', ts: new Date().toLocaleTimeString() },
    { id: 'ns=1;s=Data/MyFloat', name: 'MyFloat', value: 84.15, type: 'Float', status: 'Good', ts: new Date().toLocaleTimeString() },
    { id: 'ns=1;s=Data/MySwitch', name: 'MySwitch', value: 'True', type: 'Boolean', status: 'Good', ts: new Date().toLocaleTimeString() },
    { id: 'ns=1;s=Server/ServerStatus', name: 'ServerStatus', value: 'Running', type: 'String', status: 'Good', ts: new Date().toLocaleTimeString() },
    { id: 'ns=1;s=Data/SystemLoad', name: 'SystemLoad', value: 14.5, type: 'Double', status: 'Good', ts: new Date().toLocaleTimeString() },
  ]);

  // Connect on mount
  onMount(() => {
    wsManager.connect();
  });

  // Cleanup on destroy
  onDestroy(() => {
    stopAutoPing();
    wsManager.disconnect();
  });

  // Append new websocket pongs to the time series chart data & update simulated tag values
  $effect(() => {
    const pongs = wsManager.pongs;
    if (pongs.length > 0) {
      const latestPong = pongs[0];
      const exists = timeSeriesData.some(d => d.timestamp === latestPong.timestamp);
      if (!exists) {
        const lastVal = timeSeriesData.length > 0 ? timeSeriesData[timeSeriesData.length - 1].value : 50;
        const change = (Math.random() - 0.5) * 20;
        const newVal = Math.max(10, Math.min(95, lastVal + change));
        
        timeSeriesData = [...timeSeriesData, {
          timestamp: latestPong.timestamp,
          value: parseFloat(newVal.toFixed(1))
        }].slice(-20);

        // Update mock node values dynamically
        const randomValue = Math.floor(Math.random() * 100);
        nodeValues = nodeValues.map(node => {
          if (node.name === 'MyInt') {
            return { ...node, value: randomValue, ts: new Date().toLocaleTimeString() };
          }
          if (node.name === 'SystemLoad') {
            const loadChange = (Math.random() - 0.5) * 5;
            const newLoad = Math.max(5, Math.min(95, node.value + loadChange));
            return { ...node, value: parseFloat(newLoad.toFixed(1)), ts: new Date().toLocaleTimeString() };
          }
          return node;
        });
      }
    }
  });

  // Derived state for searches
  let filteredNodes = $derived(
    nodeValues.filter(node => 
      node.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      node.id.toLowerCase().includes(searchQuery.toLowerCase())
    )
  );

  function handleNavbarSelect(tab: string) {
    if (['login', 'logout', 'profile'].includes(tab)) {
      userAction = tab;
    } else {
      activeTab = tab;
    }
  }

  function handleLogin() {
    isLoggedIn = true;
    userAction = null;
  }

  function handleLogout() {
    isLoggedIn = false;
    userAction = null;
  }

  function sendPing() {
    wsManager.sendPing(pingText);
  }

  function toggleAutoPing() {
    if (autoPingActive) {
      stopAutoPing();
    } else {
      startAutoPing();
    }
  }

  function startAutoPing() {
    autoPingActive = true;
    autoPingIntervalId = setInterval(() => {
      const randomId = Math.floor(Math.random() * 1000);
      wsManager.sendPing(`Auto Ping #${randomId}`);
    }, 2000);
  }

  function stopAutoPing() {
    autoPingActive = false;
    if (autoPingIntervalId) {
      clearInterval(autoPingIntervalId);
      autoPingIntervalId = null;
    }
  }

  function runLuaScript() {
    isRunningLua = true;
    luaConsoleLogs = ["Initializing Lua engine v5.4...", "Connecting to OPC-UA address space..."];
    setTimeout(() => {
      luaConsoleLogs = [...luaConsoleLogs, "Running automation script..."];
    }, 400);
    setTimeout(() => {
      const targetInt = nodeValues.find(n => n.name === 'MyInt')?.value || 42;
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

<div class="app-layout">
  <Navbar onSelect={handleNavbarSelect} currentTab={activeTab} />

  <main class="main-content">
    {#if activeTab === 'home'}
      <div class="dashboard-header">
        <h1>Nunki Control Panel</h1>
        <p>Real-time Industrial Monitoring & OPC-UA Client Interface</p>
      </div>

      <div class="grid-layout">
        <!-- Ping Controls Card -->
        <div class="glass-card ping-card">
          <h3>WebSocket / STOMP Ping Loop</h3>
          <p class="subtitle">Test async connectivity with Spring Boot backend</p>
          
          <div class="input-group">
            <input type="text" bind:value={pingText} placeholder="Enter ping text" />
            <button onclick={sendPing} disabled={!wsManager.connected} class="btn-send">
              <Send size={16} />
              <span>Send Ping</span>
            </button>
          </div>

          <div class="divider"><span>OR</span></div>

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

        <!-- Ping Logs Card -->
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

    {:else if activeTab === 'diagrams'}
      <div class="dashboard-header">
        <h1>Process Synoptics</h1>
        <p>Interactive process flow diagrams linked with dynamic sensor states</p>
      </div>
      <div class="glass-card full-card">
        <SynopticView />
      </div>

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
        <TimeSeriesChart data={timeSeriesData} />
      </div>

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

    {:else if activeTab === 'values-search'}
      <div class="dashboard-header">
        <h1>Search Address Space</h1>
        <p>Query nodes dynamically across all active registers</p>
      </div>
      <div class="glass-card full-card">
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

    {:else if activeTab === 'tree'}
      <div class="dashboard-header">
        <h1>OPC-UA Address Space</h1>
        <p>Interactive view of active nodes, folders and attributes</p>
      </div>
      <div class="glass-card full-card">
        <div class="tree-mockup">
          <div class="tree-item folder">
            <span class="icon">📁</span> <span class="name">Root</span>
            <div class="tree-children">
              <div class="tree-item folder">
                <span class="icon">📁</span> <span class="name">Objects</span>
                <div class="tree-children">
                  <div class="tree-item folder">
                    <span class="icon">📁</span> <span class="name">Server</span>
                  </div>
                  <div class="tree-item folder">
                    <span class="icon">📁</span> <span class="name">Data</span>
                    <div class="tree-children">
                      <div class="tree-item variable">
                        <span class="icon">🔢</span> <span class="name">MyInt</span> <span class="value">[Int32: {nodeValues.find(n => n.name === 'MyInt')?.value || 42}]</span> <span class="badge read">Read</span> <span class="badge write">Write</span>
                      </div>
                      <div class="tree-item variable">
                        <span class="icon">📈</span> <span class="name">MyFloat</span> <span class="value">[Float: 84.15]</span> <span class="badge read">Read</span>
                      </div>
                      <div class="tree-item variable">
                        <span class="icon">🔌</span> <span class="name">MySwitch</span> <span class="value">[Boolean: True]</span> <span class="badge read">Read</span> <span class="badge write">Write</span> <span class="badge subscribe">Subscribed</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

    {:else if activeTab === 'automation-lua'}
      <div class="dashboard-header">
        <h1>Lua Automation Scripting</h1>
        <p>Design event-driven scripts triggered on OPC-UA change subscriptions</p>
      </div>
      <div class="grid-layout">
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

<!-- Modal Dialog Backdrops -->
{#if userAction}
  <div class="modal-backdrop" 
       onclick={(e) => { if (e.target === e.currentTarget) userAction = null; }} 
       onkeydown={(e) => { if (e.key === 'Escape') userAction = null; }}
       role="button"
       tabindex="-1"
       aria-label="Close modal">
    <div class="modal-content">
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
      {:else if userAction === 'logout'}
        <h3>Confirm Log Out</h3>
        <p>Are you sure you want to log out of the control panel?</p>
        <div class="modal-actions">
          <button class="btn-cancel" onclick={() => userAction = null}>Cancel</button>
          <button class="btn-danger" onclick={handleLogout}>Log Out</button>
        </div>
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

  /* Tree Mockup styling */
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
<script lang="ts">
  /**
   * @component SynopticView
   * @description Architecture Component: Industrial Mimic / Process HMI Layer.
   * Renders an interactive process flow diagram using vector graphics (inline SVG).
   * Models storage vessel levels, inlet pipeline loops, pump impellers, and output drain systems.
   * 
   * Mimic Features:
   * - Feed Pump: Visual spinning indicator active during running state.
   * - Storage Tank T-101: Renders a fluid column proportional to the tank level with measurement ticks.
   * - Drain Valve: Keyboard-navigable (`tabindex="0"`, custom Space/Enter handler) standard instrumentation icon.
   * - Pipeline animation: Dynamic dashed stroke offsets representing flow velocity.
   * 
   * System Requirements Satisfied:
   * - REQ-00021: Display dynamic SVG diagrams and synoptics (live telemetry mimics).
   * - TSK-00023.1: SVG & Charting Prerequisites / Native Synoptic SVG (reusable and lightweight SVG).
   */

  import { wsManager } from './websocket.svelte';

  // Define component props using Svelte 5 `$props` rune
  let {
    pumpRunningNodeId = '',
    valveOpenNodeId = '',
    tankLevelNodeId = ''
  } = $props<{
    pumpRunningNodeId?: string;
    valveOpenNodeId?: string;
    tankLevelNodeId?: string;
  }>();

  // --- Dynamic Simulator State Variables (Derived from real OPC-UA node states) ---
  // Valve State (true = OPEN, fluid drains out; false = CLOSED, fluid is blocked)
  let valveOpen = $derived(
    valveOpenNodeId && (
      wsManager.opcUaUpdates[valveOpenNodeId]?.value === 'true' ||
      wsManager.opcUaUpdates[valveOpenNodeId]?.value === 'True'
    )
  );
  
  // Storage Tank Water Level (ranges from 0% [empty] to 100% [maximum capacity])
  let tankLevel = $derived(
    tankLevelNodeId && wsManager.opcUaUpdates[tankLevelNodeId]?.value !== undefined
      ? parseFloat(wsManager.opcUaUpdates[tankLevelNodeId].value)
      : 45
  );
  
  // Inlet Feed Pump State (true = RUNNING, pushes fluid in; false = STOPPED)
  let pumpRunning = $derived(
    pumpRunningNodeId && (
      wsManager.opcUaUpdates[pumpRunningNodeId]?.value === 'true' ||
      wsManager.opcUaUpdates[pumpRunningNodeId]?.value === 'True'
    )
  );

  // Dash displacement offset (in pixels) for fluid movement stroke animations
  let flowOffset = $state(0);

  // Dispatch real OPC-UA write actions via REST endpoints
  async function togglePump() {
    if (!pumpRunningNodeId) return;
    const nextState = !pumpRunning;
    await wsManager.writeOpcUaValue(pumpRunningNodeId, String(nextState), 'Boolean');
  }

  async function toggleValve() {
    if (!valveOpenNodeId) return;
    const nextState = !valveOpen;
    await wsManager.writeOpcUaValue(valveOpenNodeId, String(nextState), 'Boolean');
  }

  async function handleSliderChange(valStr: string) {
    if (!tankLevelNodeId) return;
    await wsManager.writeOpcUaValue(tankLevelNodeId, valStr, 'Double');
  }

  /**
   * Flow Offset Animation Loop.
   * Shift the offset of pipeline dashed lines if flow is active.
   */
  $effect(() => {
    let intervalId: any;
    if ((pumpRunning && valveOpen) || (valveOpen && tankLevel > 5)) {
      intervalId = setInterval(() => {
        flowOffset = (flowOffset + 2) % 20; // Loops dash offsets within a 20px window
      }, 50);
    }
    // Svelte 5 cleanup block: destroys intervals on state transitions or component unmount
    return () => clearInterval(intervalId);
  });
</script>

<div class="synoptic-container">
  <!-- Top Simulator Control Deck -->
  <div class="controls">
    <!-- Toggle Feed Pump State -->
    <button onclick={togglePump} class:active={pumpRunning}>
      {pumpRunning ? 'Stop Pump' : 'Start Pump'}
    </button>
    
    <!-- Toggle Drain Valve State -->
    <button onclick={toggleValve} class:active={valveOpen}>
      {valveOpen ? 'Close Valve' : 'Open Valve'}
    </button>
    
    <!-- Manual Level Override Slider -->
    <div class="level-control">
      <label for="tank-level-slider">Tank Level: {tankLevel.toFixed(0)}%</label>
      <input id="tank-level-slider" type="range" min="0" max="100" value={tankLevel} oninput={(e) => handleSliderChange(e.currentTarget.value)} />
    </div>
  </div>

  <!-- Drawing Board Viewport Frame -->
  <div class="mimic-frame">
    <svg viewBox="0 0 800 400" width="100%" height="100%">
      <defs>
        <!-- Tank curvature gradient: mimics cylindrical metallic sheet highlighting -->
        <linearGradient id="tank-grad" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stop-color="#4a5568" />
          <stop offset="30%" stop-color="#718096" />
          <stop offset="70%" stop-color="#718096" />
          <stop offset="100%" stop-color="#2d3748" />
        </linearGradient>
        
        <!-- Liquid color gradient: transitions from light blue at the surface to deep blue at the base -->
        <linearGradient id="liquid-grad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#3182ce" />
          <stop offset="100%" stop-color="#2b6cb0" />
        </linearGradient>
      </defs>

      <!-- Mimic backing card border wrapper -->
      <rect width="800" height="400" fill="var(--code-bg)" rx="8" stroke="var(--border)" stroke-width="1" />

      <!-- ==========================================
           --- PIPELINE NETWORK LAYERS ---
           ========================================== -->

      <!-- Inlet Pipeline Section: Starts at coordinate (50, 150), leads into storage tank T-101 at (250, 250) -->
      <!-- Thick dark steel background pipeline shadow -->
      <path d="M 50 150 L 250 150 L 250 250" fill="none" stroke="#4a5568" stroke-width="12" stroke-linecap="round" />
      <!-- Pipeline fluid core: turns blue if pump is running and valve is open -->
      <path d="M 50 150 L 250 150 L 250 250" fill="none" stroke={pumpRunning && valveOpen ? '#3182ce' : '#2d3748'} stroke-width="6" stroke-linecap="round" />

      <!-- Active flow animation overlay layer -->
      {#if pumpRunning && valveOpen}
        <path d="M 50 150 L 250 150 L 250 250" fill="none" stroke="#90cdf4" stroke-width="4" stroke-dasharray="10 10" stroke-dashoffset={flowOffset} stroke-linecap="round" />
      {/if}

      <!-- Outlet Pipeline Section: Starts at coordinate (450, 300), drains out to (650, 200) -->
      <!-- Background pipeline structure -->
      <path d="M 450 300 L 650 300 L 650 200" fill="none" stroke="#4a5568" stroke-width="12" />
      <!-- Fluid core: turns blue if drain valve is open and fluid is draining -->
      <path d="M 450 300 L 650 300 L 650 200" fill="none" stroke={valveOpen && tankLevel > 5 ? '#3182ce' : '#2d3748'} stroke-width="6" />

      <!-- Active drainage animation overlay layer -->
      {#if valveOpen && tankLevel > 5}
        <path d="M 450 300 L 650 300 L 650 200" fill="none" stroke="#90cdf4" stroke-width="4" stroke-dasharray="10 10" stroke-dashoffset={-flowOffset} />
      {/if}

      <!-- ==========================================
           --- PROCESS FEED PUMP ELEMENT ---
           ========================================== -->
      <g transform="translate(130, 150)" class="pump-group">
        <!-- Pump stator body casing -->
        <circle r="25" fill="#4a5568" stroke="#cbd5e0" stroke-width="2" />
        <!-- Impeller blades: spinning class is appended reactively when pump is running -->
        <path d="M -15 -15 L 15 15 M -15 15 L 15 -15" stroke="#cbd5e0" stroke-width="3" class="pump-blades" class:spinning={pumpRunning} />
        <!-- Status pilot light (green = active operational; red = stopped) -->
        <circle r="8" fill={pumpRunning ? '#48bb78' : '#e53e3e'} />
        <text y="40" text-anchor="middle" font-size="12" fill="var(--text)" font-family="sans-serif">Feed Pump</text>
      </g>

      <!-- ==========================================
           --- PROCESS DRAIN VALVE ELEMENT ---
           ========================================== -->
      <!-- Bounded key handlers and accessibility traits map standard keyboard interactions -->
      <g transform="translate(550, 300)" 
         onclick={toggleValve} 
         onkeydown={(e) => { if (e.key === 'Enter' || e.key === ' ') { toggleValve(); e.preventDefault(); } }}
         role="button"
         tabindex="0"
         aria-label="Drain Valve Toggle"
         class="valve-group" 
         style="cursor: pointer; outline: none;">
        <!-- Hourglass instrument symbol: filled with status colors (green = open; red = closed) -->
        <path d="M -20 -15 L 20 15 L 20 -15 L -20 15 Z" fill={valveOpen ? '#48bb78' : '#e53e3e'} stroke="#cbd5e0" stroke-width="2" />
        <!-- Center connection bolt -->
        <circle r="6" fill="#fff" />
        <text y="35" text-anchor="middle" font-size="12" fill="var(--text)" font-family="sans-serif">Drain Valve ({valveOpen ? 'Open' : 'Closed'})</text>
      </g>

      <!-- ==========================================
           --- STORAGE VESSEL ELEMENT (T-101) ---
           ========================================== -->
      <g transform="translate(250, 100)">
        <!-- Cylindrical metallic container shell -->
        <rect x="0" y="0" width="200" height="220" fill="url(#tank-grad)" rx="10" stroke="#a0aec0" stroke-width="3" />
        <!-- Liquid level bar: calculates visual height relative to the tankLevel state.
             Formula: Y_start = 215 - (level/100) * 210, Height = (level/100) * 210 -->
        <rect x="5" y={215 - (tankLevel / 100) * 210} width="190" height={(tankLevel / 100) * 210} fill="url(#liquid-grad)" rx="5" />
        
        <!-- Measurement indicators loop -->
        {#each [0, 25, 50, 75, 100] as mark}
          <line x1="180" y1={215 - (mark / 100) * 210} x2="195" y2={215 - (mark / 100) * 210} stroke="#ffffff" stroke-width="1.5" stroke-opacity="0.6" />
          <text x="175" y={218 - (mark / 100) * 210} font-size="10" fill="#ffffff" fill-opacity="0.8" text-anchor="end" font-family="monospace">{mark}%</text>
        {/each}

        <!-- Outer Label -->
        <text x="100" y="-15" text-anchor="middle" font-size="16" font-weight="bold" fill="var(--text-h)" font-family="sans-serif">Storage Tank T-101</text>
      </g>

    </svg>
  </div>
</div>

<style>
  /* Flex containment */
  .synoptic-container {
    display: flex;
    flex-direction: column;
    gap: 20px;
    height: 100%;
  }
  
  /* Top alignment configuration panel */
  .controls {
    display: flex;
    gap: 15px;
    align-items: center;
    background: var(--code-bg);
    padding: 15px;
    border-radius: 8px;
    border: 1px solid var(--border);
  }
  
  .level-control {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-left: auto;
  }
  
  .level-control label {
    font-size: 14px;
    font-weight: 500;
    color: var(--text-h);
  }
  
  input[type="range"] {
    cursor: pointer;
    accent-color: var(--accent);
  }
  
  button {
    padding: 8px 16px;
    border-radius: 6px;
    border: 1px solid var(--border);
    background: var(--bg);
    color: var(--text);
    cursor: pointer;
    font-weight: 500;
    transition: all 0.2s ease;
  }
  
  button:hover {
    border-color: var(--accent);
    color: var(--accent);
  }
  
  button.active {
    background: var(--accent);
    color: white;
    border-color: var(--accent);
  }
  
  .mimic-frame {
    flex-grow: 1;
    border-radius: 8px;
    overflow: hidden;
  }
  
  /* Pump rotating animation triggered by active css state */
  .spinning {
    transform-origin: center;
    animation: spin 2s linear infinite;
  }
  
  @keyframes spin {
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
  }
</style>



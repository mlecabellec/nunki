<script lang="ts">
  /*
   * REQ-00021 – ability to display dynamic SVG diagrams and synoptics
   * TSK-00023.1 – SVG & Charting Prerequisites / Native Synoptic SVG
   */

  // Valve state
  let valveOpen = $state(false);
  // Tank Level State (0 to 100)
  let tankLevel = $state(45);
  // Pump running State
  let pumpRunning = $state(false);

  // Interval for simulating state animations
  let flowOffset = $state(0);
  $effect(() => {
    let intervalId: any;
    if (pumpRunning && valveOpen) {
      intervalId = setInterval(() => {
        flowOffset = (flowOffset + 2) % 20;
        // Slowly change tank level
        if (tankLevel < 95) {
          tankLevel += 0.5;
        }
      }, 50);
    } else if (!pumpRunning && tankLevel > 10) {
      // Slow drain if pump is off but valve is open
      if (valveOpen) {
        intervalId = setInterval(() => {
          tankLevel -= 0.3;
        }, 100);
      }
    }
    return () => clearInterval(intervalId);
  });
</script>

<div class="synoptic-container">
  <div class="controls">
    <button onclick={() => pumpRunning = !pumpRunning} class:active={pumpRunning}>
      {pumpRunning ? 'Stop Pump' : 'Start Pump'}
    </button>
    <button onclick={() => valveOpen = !valveOpen} class:active={valveOpen}>
      {valveOpen ? 'Close Valve' : 'Open Valve'}
    </button>
    <div class="level-control">
      <label for="tank-level-slider">Tank Level: {tankLevel.toFixed(0)}%</label>
      <input id="tank-level-slider" type="range" min="0" max="100" bind:value={tankLevel} />
    </div>
  </div>

  <div class="mimic-frame">
    <svg viewBox="0 0 800 400" width="100%" height="100%">
      <defs>
        <!-- Gradients -->
        <linearGradient id="tank-grad" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stop-color="#4a5568" />
          <stop offset="30%" stop-color="#718096" />
          <stop offset="70%" stop-color="#718096" />
          <stop offset="100%" stop-color="#2d3748" />
        </linearGradient>
        <linearGradient id="liquid-grad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#3182ce" />
          <stop offset="100%" stop-color="#2b6cb0" />
        </linearGradient>
      </defs>

      <!-- Grid or Background elements -->
      <rect width="800" height="400" fill="var(--code-bg)" rx="8" stroke="var(--border)" stroke-width="1" />

      <!-- PIPES -->
      <!-- Input Pipe -->
      <path d="M 50 150 L 250 150 L 250 250" fill="none" stroke="#4a5568" stroke-width="12" stroke-linecap="round" />
      <path d="M 50 150 L 250 150 L 250 250" fill="none" stroke={pumpRunning && valveOpen ? '#3182ce' : '#2d3748'} stroke-width="6" stroke-linecap="round" />

      <!-- Flow animation dashes inside input pipe -->
      {#if pumpRunning && valveOpen}
        <path d="M 50 150 L 250 150 L 250 250" fill="none" stroke="#90cdf4" stroke-width="4" stroke-dasharray="10 10" stroke-dashoffset={flowOffset} stroke-linecap="round" />
      {/if}

      <!-- Output Pipe -->
      <path d="M 450 300 L 650 300 L 650 200" fill="none" stroke="#4a5568" stroke-width="12" />
      <path d="M 450 300 L 650 300 L 650 200" fill="none" stroke={valveOpen && tankLevel > 5 ? '#3182ce' : '#2d3748'} stroke-width="6" />

      {#if valveOpen && tankLevel > 5}
        <path d="M 450 300 L 650 300 L 650 200" fill="none" stroke="#90cdf4" stroke-width="4" stroke-dasharray="10 10" stroke-dashoffset={-flowOffset} />
      {/if}

      <!-- PUMP (on input line) -->
      <g transform="translate(130, 150)" class="pump-group">
        <circle r="25" fill="#4a5568" stroke="#cbd5e0" stroke-width="2" />
        <path d="M -15 -15 L 15 15 M -15 15 L 15 -15" stroke="#cbd5e0" stroke-width="3" class="pump-blades" class:spinning={pumpRunning} />
        <circle r="8" fill={pumpRunning ? '#48bb78' : '#e53e3e'} />
        <text y="40" text-anchor="middle" font-size="12" fill="var(--text)" font-family="sans-serif">Feed Pump</text>
      </g>

      <!-- VALVE (on output line) -->
      <g transform="translate(550, 300)" 
         onclick={() => valveOpen = !valveOpen} 
         onkeydown={(e) => { if (e.key === 'Enter' || e.key === ' ') { valveOpen = !valveOpen; e.preventDefault(); } }}
         role="button"
         tabindex="0"
         aria-label="Drain Valve Toggle"
         class="valve-group" 
         style="cursor: pointer; outline: none;">
        <path d="M -20 -15 L 20 15 L 20 -15 L -20 15 Z" fill={valveOpen ? '#48bb78' : '#e53e3e'} stroke="#cbd5e0" stroke-width="2" />
        <circle r="6" fill="#fff" />
        <text y="35" text-anchor="middle" font-size="12" fill="var(--text)" font-family="sans-serif">Drain Valve ({valveOpen ? 'Open' : 'Closed'})</text>
      </g>

      <!-- PROCESS TANK -->
      <g transform="translate(250, 100)">
        <!-- Outer Tank Structure -->
        <rect x="0" y="0" width="200" height="220" fill="url(#tank-grad)" rx="10" stroke="#a0aec0" stroke-width="3" />
        <!-- Tank liquid (fill level) -->
        <rect x="5" y={215 - (tankLevel / 100) * 210} width="190" height={(tankLevel / 100) * 210} fill="url(#liquid-grad)" rx="5" />
        
        <!-- Tank scale markers -->
        {#each [0, 25, 50, 75, 100] as mark}
          <line x1="180" y1={215 - (mark / 100) * 210} x2="195" y2={215 - (mark / 100) * 210} stroke="#ffffff" stroke-width="1.5" stroke-opacity="0.6" />
          <text x="175" y={218 - (mark / 100) * 210} font-size="10" fill="#ffffff" fill-opacity="0.8" text-anchor="end" font-family="monospace">{mark}%</text>
        {/each}

        <!-- Label -->
        <text x="100" y="-15" text-anchor="middle" font-size="16" font-weight="bold" fill="var(--text-h)" font-family="sans-serif">Storage Tank T-101</text>
      </g>

    </svg>
  </div>
</div>

<style>
  .synoptic-container {
    display: flex;
    flex-direction: column;
    gap: 20px;
    height: 100%;
  }
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
  .spinning {
    transform-origin: center;
    animation: spin 2s linear infinite;
  }
  @keyframes spin {
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
  }
</style>

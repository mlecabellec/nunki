<script lang="ts">
  /**
   * @component TimeSeriesChart
   * @description Architecture Component: Data Visualization Layer.
   * Renders a real-time responsive SVG area line graph designed to map historical time-series datasets.
   * Calculates boundaries reactively using Svelte 5 derived states and handles window resize scales dynamically.
   * 
   * Design Features:
   * - Responsive viewbox scaling based on binding bounds (`bind:clientWidth`/`bind:clientHeight`).
   * - Native mathematical coordinate scaling (avoids heavy D3 external dependencies).
   * - Modern glassmorphism themes utilizing CSS variable definitions (`var(--accent)`, `var(--code-bg)`).
   * 
   * System Requirements Satisfied:
   * - REQ-00022: Display graph of time series (live telemetry plotting).
   * - TSK-00023.1: SVG & Charting Prerequisites / Native SVG Chart (pure lightweight inline SVG renderer).
   */

  /**
   * DataPoint interface representing coordinates on the chart.
   */
  interface DataPoint {
    timestamp: number; // Unix timestamp in milliseconds (maps to X-Axis coordinate)
    value: number;     // Numeric sensor frequency amplitude metric (maps to Y-Axis coordinate)
  }

  // Props declaration using Svelte 5 `$props` rune.
  // - `data`: Collection of coordinates to draw, defaults to an empty array.
  let { data = [] }: { data: DataPoint[] } = $props();

  // Reactive layout width and height states, bounded directly to the wrapper container dimensions
  let width = $state(600);
  let height = $state(300);
  
  // Padding zones to keep text scales from clipping beyond viewport boundaries
  const padding = { top: 20, right: 20, bottom: 40, left: 50 };

  // ==========================================
  // --- MATHEMATICAL MATH COORDINATES SCALING ---
  // ==========================================
  
  // Calculate horizontal domain boundaries (timestamps)
  let minX = $derived(data.length > 0 ? Math.min(...data.map(d => d.timestamp)) : 0);
  let maxX = $derived(data.length > 0 ? Math.max(...data.map(d => d.timestamp)) : 100);
  
  // Calculate vertical domain boundaries (keeps a minimum baseline range of 0 to 100)
  let minY = $derived(data.length > 0 ? Math.min(0, ...data.map(d => d.value)) : 0);
  let maxY = $derived(data.length > 0 ? Math.max(100, ...data.map(d => d.value)) : 100);

  /**
   * Linear Interpolation: Maps a timestamp domain value to a pixel X-coordinate coordinate.
   * Formula: X = padding_left + ((value - min_val) / (max_val - min_val)) * available_draw_width
   * 
   * @param {number} val - Unix timestamp.
   * @returns {number} Pixel column column position.
   */
  const getX = (val: number) => {
    if (maxX === minX) return padding.left;
    return padding.left + ((val - minX) / (maxX - minX)) * (width - padding.left - padding.right);
  };

  /**
   * Linear Interpolation: Maps a sensor amplitude value to a pixel Y-coordinate coordinate.
   * Note: In SVG viewports, the origin (0,0) resides in the TOP-LEFT corner.
   * Therefore, Y scaling is inverted:
   * Formula: Y = height - padding_bottom - ((value - min_val) / (max_val - min_val)) * available_draw_height
   * 
   * @param {number} val - Sensor value.
   * @returns {number} Pixel line row position.
   */
  const getY = (val: number) => {
    if (maxY === minY) return height - padding.bottom;
    return height - padding.bottom - ((val - minY) / (maxY - minY)) * (height - padding.top - padding.bottom);
  };

  // Convert raw records array into mapped coordinates
  let points = $derived(data.map(d => ({ x: getX(d.timestamp), y: getY(d.value) })));

  // Creates the main SVG outline path string (using standard Move (M) and Line-to (L) tags)
  let pathString = $derived(
    points.length > 0
      ? `M ${points[0].x} ${points[0].y} ` + points.slice(1).map(p => `L ${p.x} ${p.y}`).join(' ')
      : ''
  );

  // Creates the closed polygon path string enclosing the region underneath the line.
  // Extends path down to the baseline axis to form a complete shape fillable by gradients.
  let areaString = $derived(
    points.length > 0
      ? `${pathString} L ${points[points.length - 1].x} ${height - padding.bottom} L ${points[0].x} ${height - padding.bottom} Z`
      : ''
  );

  // Computes the coordinates of five gridline tracks spaced evenly along the vertical scale
  let yGridLines = $derived.by(() => {
    const lines = [];
    const step = (maxY - minY) / 5;
    for (let i = 0; i <= 5; i++) {
      const val = minY + step * i;
      lines.push({ val: val.toFixed(1), y: getY(val) });
    }
    return lines;
  });
</script>

<!-- 
  Responsive Container Bounded via bind:clientWidth and bind:clientHeight
  Automatically triggers re-computation of math domains whenever the parent element scales.
-->
<div class="chart-container" bind:clientWidth={width} bind:clientHeight={height}>
  {#if data.length === 0}
    <div class="no-data">No time series data available</div>
  {:else}
    <svg {width} {height}>
      <defs>
        <!-- Linear gradient fill providing a fading neon trace glow effect -->
        <linearGradient id="chart-area-grad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="var(--accent)" stop-opacity="0.3" />
          <stop offset="100%" stop-color="var(--accent)" stop-opacity="0.0" />
        </linearGradient>
      </defs>

      <!-- Horizontal grid tracks and Y-axis text tags -->
      {#each yGridLines as line}
        <line x1={padding.left} y1={line.y} x2={width - padding.right} y2={line.y} class="grid-line" />
        <text x={padding.left - 10} y={line.y + 4} class="axis-text axis-label-y">{line.val}</text>
      {/each}

      <!-- Bottom baseline axis -->
      <line x1={padding.left} y1={height - padding.bottom} x2={width - padding.right} y2={height - padding.bottom} class="axis-line" />

      <!-- Faded fill vector polygon -->
      <path d={areaString} fill="url(#chart-area-grad)" />

      <!-- Primary stroke line path -->
      <path d={pathString} fill="none" stroke="var(--accent)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" class="line-path" />

      <!-- Circular point node markers highlighting individual timestamps -->
      {#each points as pt}
        <circle cx={pt.x} cy={pt.y} r="4" fill="var(--bg)" stroke="var(--accent)" stroke-width="2" class="dot" />
      {/each}
    </svg>
  {/if}
</div>

<style>
  /* Bounding wrapper, integrating into system UI theme cards */
  .chart-container {
    width: 100%;
    height: 100%;
    position: relative;
    background: var(--code-bg);
    border-radius: 8px;
    padding: 10px;
    box-sizing: border-box;
    border: 1px solid var(--border);
  }
  
  .no-data {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    color: var(--text);
    font-size: 14px;
  }
  
  svg {
    display: block;
    width: 100%;
    height: 100%;
    overflow: visible;
  }
  
  /* Horizontal divider guides styling */
  .grid-line {
    stroke: var(--border);
    stroke-dasharray: 4 4;
    stroke-width: 1;
  }
  
  /* Base horizontal boundary coordinate axis line */
  .axis-line {
    stroke: var(--border);
    stroke-width: 2;
  }
  
  /* Font specifications matching native developer monospace aesthetics */
  .axis-text {
    font-size: 11px;
    fill: var(--text);
    text-anchor: end;
    font-family: var(--mono);
  }
  
  /* Adds a neon drop shadow underneath the active trend path line */
  .line-path {
    filter: drop-shadow(0 2px 8px rgba(170, 59, 255, 0.4));
  }
  
  /* Interactive points transition hover scaling triggers */
  .dot {
    transition: r 0.2s ease;
    cursor: pointer;
  }
  
  .dot:hover {
    r: 6;
  }
</style>
